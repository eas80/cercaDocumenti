package com.example.documentstore.repository.cloudinary;

import com.example.documentstore.model.DocumentEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Everything about a document except its raw bytes, persisted as
 * {@code <id>.meta.json}. The actual content lives on Cloudinary; {@code
 * cloudinaryUrl} is the exact {@code secure_url} returned by the last
 * upload, stored verbatim because a version-less Cloudinary delivery URL
 * does not reliably resolve.
 */
record CloudinaryDocumentMetadata(
        String id,
        String name,
        String description,
        String contentType,
        long sizeBytes,
        Instant lastModifiedDate,
        String cloudinaryUrl
) {

    @JsonCreator
    CloudinaryDocumentMetadata(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("sizeBytes") long sizeBytes,
            @JsonProperty("lastModifiedDate") Instant lastModifiedDate,
            @JsonProperty("cloudinaryUrl") String cloudinaryUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.lastModifiedDate = lastModifiedDate;
        this.cloudinaryUrl = cloudinaryUrl;
    }

    static CloudinaryDocumentMetadata from(DocumentEntity entity, String cloudinaryUrl) {
        return new CloudinaryDocumentMetadata(
                entity.id(), entity.name(), entity.description(), entity.contentType(),
                entity.sizeBytes(), entity.lastModifiedDate(), cloudinaryUrl);
    }

    DocumentEntity toEntity(byte[] content) {
        return new DocumentEntity(id, name, description, content, contentType, sizeBytes, lastModifiedDate);
    }
}
