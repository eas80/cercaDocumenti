package com.example.documentstore.repository.disk;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-based {@link DocumentRepository}. Each document is stored as two files
 * in {@code documentstore.storage.disk.directory}:
 * <ul>
 *     <li>{@code <id>.meta.json} - id, name, description, contentType, size, lastModifiedDate</li>
 *     <li>{@code <id>.content.bin} - raw document bytes</li>
 * </ul>
 * Writes go through a temp file + atomic move so a reader never observes a
 * half-written file. This is the default implementation (active unless
 * {@code documentstore.storage.type=mongo}); swap in a Spring Data MongoDB
 * implementation later without touching callers, see README.md.
 */
@Repository
@ConditionalOnProperty(prefix = "documentstore.storage", name = "type", havingValue = "disk", matchIfMissing = true)
public class DiskDocumentRepository implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(DiskDocumentRepository.class);

    private static final String META_SUFFIX = ".meta.json";
    private static final String CONTENT_SUFFIX = ".content.bin";

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    public DiskDocumentRepository(
            @Value("${documentstore.storage.disk.directory:./data/documents}") String directory,
            ObjectMapper objectMapper) {
        this.storageDir = Path.of(directory);
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + storageDir, e);
        }
        log.info("STORAGE: active backend is disk (documentstore.storage.type='disk' or unset), directory={}", storageDir);
    }

    @Override
    public Optional<DocumentEntity> findById(String id) {
        Path metaPath = metaPath(id);
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        DocumentMetadata metadata = readMetadata(metaPath);
        byte[] content = readBytes(contentPath(id));
        return Optional.of(metadata.toEntity(content));
    }

    @Override
    public List<DocumentEntity> search(DocumentSearchCriteria criteria) {
        try (Stream<Path> files = Files.list(storageDir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(META_SUFFIX))
                    .map(this::readMetadata)
                    .filter(m -> matches(m, criteria))
                    .sorted(Comparator.comparing(DocumentMetadata::lastModifiedDate).reversed())
                    .map(m -> m.toEntity(null))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list documents in " + storageDir, e);
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

        // Write content first so metadata (moved into place last) never
        // points at a missing/partial content file.
        writeAtomic(contentPath(id), toPersist.content());
        writeAtomic(metaPath(id), toJson(DocumentMetadata.from(toPersist)));

        return toPersist;
    }

    @Override
    public boolean existsById(String id) {
        return Files.exists(metaPath(id));
    }

    @Override
    public void deleteById(String id) {
        // Delete metadata first: a reader must never see metadata pointing at
        // an already-removed content file.
        deleteIfExists(metaPath(id));
        deleteIfExists(contentPath(id));
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + path, e);
        }
    }

    private boolean matches(DocumentMetadata metadata, DocumentSearchCriteria criteria) {
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
        return storageDir.resolve(id + META_SUFFIX);
    }

    private Path contentPath(String id) {
        return storageDir.resolve(id + CONTENT_SUFFIX);
    }

    private DocumentMetadata readMetadata(Path path) {
        try {
            return objectMapper.readValue(Files.readAllBytes(path), DocumentMetadata.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read metadata file " + path, e);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content file " + path, e);
        }
    }

    private byte[] toJson(DocumentMetadata metadata) {
        try {
            return objectMapper.writeValueAsBytes(metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize metadata for document " + metadata.id(), e);
        }
    }

    private void writeAtomic(Path target, byte[] bytes) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(storageDir, target.getFileName().toString(), ".tmp");
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
