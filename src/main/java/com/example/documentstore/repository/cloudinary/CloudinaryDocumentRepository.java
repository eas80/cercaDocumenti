package com.example.documentstore.repository.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentRepository;
import com.example.documentstore.repository.DocumentSearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores document content on Cloudinary (as {@code resource_type=raw}
 * assets) and everything else - name, description, size, last-modified date
 * - in local JSON metadata files, the same shape {@link
 * com.example.documentstore.repository.disk.DiskDocumentRepository} already
 * uses for its {@code .meta.json} files. Search stays a local scan for the
 * same reason the disk backend does it that way: Cloudinary's own Search API
 * would work too, but it's a paid-tier-shaped dependency this app doesn't
 * otherwise need, and the metadata is small enough that scanning it locally
 * is simpler and just as fast.
 * <p>
 * Content is never lost if this metadata directory is wiped (e.g. an
 * ephemeral container filesystem) - only the ability to list/search it is,
 * until it's re-indexed by some other means. There is currently no
 * automatic reconciliation from Cloudinary back into local metadata.
 */
@Repository
@ConditionalOnProperty(prefix = "documentstore.storage", name = "type", havingValue = "cloudinary")
public class CloudinaryDocumentRepository implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryDocumentRepository.class);

    private static final String META_SUFFIX = ".meta.json";
    private static final String PUBLIC_ID_PREFIX = "documentstore/";

    private final Cloudinary cloudinary;
    private final Path metadataDir;
    private final ObjectMapper objectMapper;

    public CloudinaryDocumentRepository(
            @Value("${documentstore.storage.cloudinary.cloud-name:}") String cloudName,
            @Value("${documentstore.storage.cloudinary.api-key:}") String apiKey,
            @Value("${documentstore.storage.cloudinary.api-secret:}") String apiSecret,
            @Value("${documentstore.storage.cloudinary.metadata-directory:./data/cloudinary-metadata}") String metadataDirectory,
            ObjectMapper objectMapper) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
        this.metadataDir = Path.of(metadataDirectory);
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(this.metadataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create metadata directory " + this.metadataDir, e);
        }

        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("STORAGE: active backend is cloudinary, but cloud-name/api-key/api-secret look empty "
                    + "(cloud-name='{}', api-key blank={}, api-secret blank={}) - every upload will fail with a "
                    + "Cloudinary auth error until DOCUMENTSTORE_STORAGE_CLOUDINARY_* env vars actually reach this "
                    + "process (see the CORS:/AUTH: diagnostic log lines for the same class of problem)",
                    cloudName, apiKey.isBlank(), apiSecret.isBlank());
        } else {
            log.info("STORAGE: active backend is cloudinary, cloud-name={}, metadata directory={}", cloudName, metadataDir);
        }
    }

    @Override
    public Optional<DocumentEntity> findById(String id) {
        Path metaPath = metaPath(id);
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        CloudinaryDocumentMetadata metadata = readMetadata(metaPath);
        byte[] content = downloadContent(metadata.cloudinaryUrl());
        return Optional.of(metadata.toEntity(content));
    }

    @Override
    public List<DocumentEntity> search(DocumentSearchCriteria criteria) {
        try (Stream<Path> files = Files.list(metadataDir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(META_SUFFIX))
                    .map(this::readMetadata)
                    .filter(m -> matches(m, criteria))
                    .sorted(Comparator.comparing(CloudinaryDocumentMetadata::lastModifiedDate).reversed())
                    .map(m -> m.toEntity(null))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list documents in " + metadataDir, e);
        }
    }

    @Override
    public DocumentEntity save(DocumentEntity document) {
        String id = document.id() != null ? document.id() : UUID.randomUUID().toString();
        DocumentEntity toPersist = new DocumentEntity(
                id,
                document.name(),
                document.description(),
                document.content(),
                document.contentType(),
                document.content() != null ? document.content().length : 0,
                Instant.now()
        );

        String secureUrl = uploadContent(id, toPersist.content());
        writeMetadata(metaPath(id), CloudinaryDocumentMetadata.from(toPersist, secureUrl));

        return toPersist;
    }

    @Override
    public boolean existsById(String id) {
        return Files.exists(metaPath(id));
    }

    @SuppressWarnings("unchecked")
    private String uploadContent(String id, byte[] content) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", PUBLIC_ID_PREFIX + id,
                    "resource_type", "raw",
                    "overwrite", true,
                    "invalidate", true
            ));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload document " + id + " to Cloudinary", e);
        }
    }

    private byte[] downloadContent(String url) {
        try {
            URL parsed = URI.create(url).toURL();
            try (var in = parsed.openStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download document content from Cloudinary: " + url, e);
        }
    }

    private boolean matches(CloudinaryDocumentMetadata metadata, DocumentSearchCriteria criteria) {
        if (criteria.nameLike() != null && !containsIgnoreCase(metadata.name(), criteria.nameLike())) {
            return false;
        }
        if (criteria.descriptionLike() != null && !containsIgnoreCase(metadata.description(), criteria.descriptionLike())) {
            return false;
        }
        LocalDate lastModifiedDay = metadata.lastModifiedDate().atZone(ZoneId.systemDefault()).toLocalDate();
        if (criteria.dateFrom() != null && lastModifiedDay.isBefore(criteria.dateFrom())) {
            return false;
        }
        if (criteria.dateTo() != null && lastModifiedDay.isAfter(criteria.dateTo())) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private Path metaPath(String id) {
        return metadataDir.resolve(id + META_SUFFIX);
    }

    private CloudinaryDocumentMetadata readMetadata(Path path) {
        try {
            return objectMapper.readValue(Files.readAllBytes(path), CloudinaryDocumentMetadata.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read metadata file " + path, e);
        }
    }

    private void writeMetadata(Path target, CloudinaryDocumentMetadata metadata) {
        Path tmp = null;
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(metadata);
            tmp = Files.createTempFile(metadataDir, target.getFileName().toString(), ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort cleanup, the move already succeeded or failed above
                }
            }
        }
    }
}
