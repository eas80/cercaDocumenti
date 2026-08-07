package com.example.documentstore.model;

import java.time.Instant;

/**
 * Storage-agnostic representation of a stored document. Both the disk-based
 * repository and any future MongoDB repository produce/consume this same type,
 * which is what lets the storage backend be swapped without touching
 * service/controller code.
 * <p>
 * {@code content} is {@code null} when the instance represents metadata only
 * (e.g. search results), and populated when a single document is fetched by id.
 */
public record DocumentEntity(
        String id,
        String name,
        String description,
        byte[] content,
        String contentType,
        long sizeBytes,
        Instant lastModifiedDate
) {
}
