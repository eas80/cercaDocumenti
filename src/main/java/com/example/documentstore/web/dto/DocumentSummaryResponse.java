package com.example.documentstore.web.dto;

import com.example.documentstore.model.DocumentEntity;

import java.time.Instant;

/**
 * Metadata-only view of a document (no content bytes) returned by the search
 * endpoint and by insert/update. Use {@code GET /api/documents/{id}} to fetch
 * the actual document content.
 */
public record DocumentSummaryResponse(
        String id,
        String name,
        String description,
        String contentType,
        long sizeBytes,
        Instant lastModifiedDate
) {
    public static DocumentSummaryResponse from(DocumentEntity entity) {
        return new DocumentSummaryResponse(
                entity.id(),
                entity.name(),
                entity.description(),
                entity.contentType(),
                entity.sizeBytes(),
                entity.lastModifiedDate()
        );
    }
}
