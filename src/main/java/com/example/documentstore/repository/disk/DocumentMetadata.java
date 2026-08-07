package com.example.documentstore.repository.disk;

import com.example.documentstore.model.DocumentEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Everything about a document except its raw bytes. Persisted as
 * {@code <id>.meta.json} so that listing/searching never has to load file
 * content into memory. Explicit {@link JsonProperty} annotations make
 * (de)serialization independent of the {@code -parameters} compiler flag.
 */
public record DocumentMetadata(
        String id,
        String name,
        String description,
        String contentType,
        long sizeBytes,
        Instant lastModifiedDate
) {

    @JsonCreator
    public DocumentMetadata(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("sizeBytes") long sizeBytes,
            @JsonProperty("lastModifiedDate") Instant lastModifiedDate
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.lastModifiedDate = lastModifiedDate;
    }

    static DocumentMetadata from(DocumentEntity entity) {
        return new DocumentMetadata(
                entity.id(),
                entity.name(),
                entity.description(),
                entity.contentType(),
                entity.sizeBytes(),
                entity.lastModifiedDate()
        );
    }

    DocumentEntity toEntity(byte[] content) {
        return new DocumentEntity(id, name, description, content, contentType, sizeBytes, lastModifiedDate);
    }
}
