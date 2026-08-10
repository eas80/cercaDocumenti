package com.example.documentstore.repository.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentRepository;
import com.example.documentstore.repository.DocumentSearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores document content on Cloudinary (as {@code resource_type=raw}
 * assets) and everything else - name, description, content type, size,
 * last-modified date - in local JSON metadata files, the same shape {@link
 * com.example.documentstore.repository.disk.DiskDocumentRepository} already
 * uses for its {@code .meta.json} files. Search stays a local scan for the
 * same reason the disk backend does it that way: Cloudinary's Search API
 * only supports trailing wildcards per token (e.g. {@code Ricerca*}), not
 * arbitrary substring matching like the rest of this app's search already
 * promises - verified empirically, not assumed.
 * <p>
 * The local metadata directory is not durable on its own (e.g. an ephemeral
 * container filesystem wiped on every redeploy) - so name/description/
 * content-type are <em>also</em> saved as Cloudinary context on every
 * upload, purely so {@link #reconcileFromCloudinary()} can rebuild the local
 * index from Cloudinary at startup if it's missing or incomplete. Cloudinary
 * itself is the durable source of truth; the local index is a rebuildable
 * cache that makes search/listing fast without depending on Cloudinary's
 * more limited query syntax.
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
    private final boolean credentialsConfigured;

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

        this.credentialsConfigured = !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
        if (!credentialsConfigured) {
            log.warn("STORAGE: active backend is cloudinary, but cloud-name/api-key/api-secret look empty "
                    + "(cloud-name='{}', api-key blank={}, api-secret blank={}) - every upload will fail with a "
                    + "Cloudinary auth error until DOCUMENTSTORE_STORAGE_CLOUDINARY_* env vars actually reach this "
                    + "process (see the CORS:/AUTH: diagnostic log lines for the same class of problem)",
                    cloudName, apiKey.isBlank(), apiSecret.isBlank());
        } else {
            log.info("STORAGE: active backend is cloudinary, cloud-name={}, metadata directory={}", cloudName, metadataDir);
        }
    }

    /**
     * Runs after the app is already accepting requests (not from this bean's
     * constructor, which runs during context startup and would otherwise
     * delay every endpoint - including login - behind this Cloudinary call).
     * A background thread keeps it off Spring's event-publishing thread too.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        if (!credentialsConfigured) {
            return;
        }
        Thread thread = new Thread(this::reconcileFromCloudinary, "cloudinary-reconcile");
        thread.setDaemon(true);
        thread.start();
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

        String secureUrl = uploadContent(toPersist);
        writeMetadata(metaPath(id), CloudinaryDocumentMetadata.from(toPersist, secureUrl));

        return toPersist;
    }

    @Override
    public boolean existsById(String id) {
        return Files.exists(metaPath(id));
    }

    @Override
    public void deleteById(String id) {
        try {
            // Cloudinary's destroy() is idempotent (returns {"result":"not found"}
            // rather than throwing when the asset is already gone).
            cloudinary.uploader().destroy(PUBLIC_ID_PREFIX + id, ObjectUtils.asMap("resource_type", "raw"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document " + id + " from Cloudinary", e);
        }
        try {
            Files.deleteIfExists(metaPath(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete metadata file " + metaPath(id), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String uploadContent(DocumentEntity document) {
        try {
            String context = "name=" + escapeContextValue(document.name())
                    + "|description=" + escapeContextValue(document.description())
                    + "|contentType=" + escapeContextValue(document.contentType());
            Map<String, Object> result = cloudinary.uploader().upload(document.content(), ObjectUtils.asMap(
                    "public_id", PUBLIC_ID_PREFIX + document.id(),
                    "resource_type", "raw",
                    "overwrite", true,
                    "invalidate", true,
                    "context", context
            ));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload document " + document.id() + " to Cloudinary", e);
        }
    }

    private static String escapeContextValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=");
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

    /**
     * Rebuilds any local metadata files missing compared to what's actually
     * on Cloudinary - the fix for the local index being wiped by a redeploy
     * on a platform without a persistent disk (documents already there
     * before their name/description/content-type were saved as context will
     * come back with a placeholder name and no description/content-type,
     * since that information was never recoverable in the first place).
     * Failures here are logged, not fatal - the app still starts and serves
     * whatever local metadata already exists.
     */
    @SuppressWarnings("unchecked")
    private void reconcileFromCloudinary() {
        Set<String> localIds = listLocalIds();
        int reconciled = 0;
        String cursor = null;
        try {
            do {
                var search = cloudinary.search()
                        .expression("resource_type:raw AND public_id:" + PUBLIC_ID_PREFIX + "*")
                        .withField("context")
                        .maxResults(500);
                if (cursor != null) {
                    search = search.nextCursor(cursor);
                }
                ApiResponse response = search.execute();
                List<Map<String, Object>> resources = (List<Map<String, Object>>) response.get("resources");
                for (Map<String, Object> resource : resources) {
                    String publicId = (String) resource.get("public_id");
                    String id = publicId.substring(PUBLIC_ID_PREFIX.length());
                    if (!localIds.contains(id)) {
                        reconcileOne(id, resource);
                        reconciled++;
                    }
                }
                cursor = (String) response.get("next_cursor");
            } while (cursor != null);
        } catch (Exception e) {
            log.warn("STORAGE: failed to reconcile the local metadata index from Cloudinary - search/listing may "
                    + "be incomplete until the next successful startup", e);
            return;
        }

        if (reconciled > 0) {
            log.info("STORAGE: reconciled {} document(s) from Cloudinary into the local metadata index "
                    + "(the local index was likely wiped by a redeploy/restart)", reconciled);
        }
    }

    private Set<String> listLocalIds() {
        try (Stream<Path> files = Files.list(metadataDir)) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(META_SUFFIX))
                    .map(name -> name.substring(0, name.length() - META_SUFFIX.length()))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + metadataDir, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void reconcileOne(String id, Map<String, Object> resource) {
        Map<String, Object> context = (Map<String, Object>) resource.get("context");
        // The Search API returns context flat; a single-resource Admin API
        // lookup nests it under "custom" instead - handle either shape.
        if (context != null && context.get("custom") instanceof Map) {
            context = (Map<String, Object>) context.get("custom");
        }

        String name = context != null && context.get("name") != null ? (String) context.get("name") : id;
        String description = context != null ? (String) context.get("description") : null;
        String contentType = context != null ? (String) context.get("contentType") : null;

        Number bytes = (Number) resource.get("bytes");
        String createdAt = (String) resource.get("created_at");
        Instant lastModified = createdAt != null ? Instant.parse(createdAt) : Instant.now();
        String secureUrl = (String) resource.get("secure_url");

        writeMetadata(metaPath(id), new CloudinaryDocumentMetadata(
                id, name, description, contentType, bytes != null ? bytes.longValue() : 0, lastModified, secureUrl));
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
