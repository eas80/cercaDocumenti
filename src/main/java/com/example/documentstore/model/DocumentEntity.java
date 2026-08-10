package com.example.documentstore.model;

import java.time.Instant;
import java.util.List;

/**
 * Storage-agnostic representation of a stored document. Both the disk-based
 * repository and any future MongoDB repository produce/consume this same type,
 * which is what lets the storage backend be swapped without touching
 * service/controller code.
 * <p>
 * {@code content} is {@code null} when the instance represents metadata only
 * (e.g. search results), and populated when a single document is fetched by id.
 * <p>
 * {@code owner} is the username that created the document; {@code null} for
 * documents created before per-user ownership existed, treated as visible to
 * everyone rather than orphaned. {@code sharedWith} lists additional usernames
 * (besides the owner) granted full access - never {@code null}, empty when
 * not shared with anyone.
 */
public record DocumentEntity(
        String id,
        String name,
        String description,
        byte[] content,
        String contentType,
        long sizeBytes,
        Instant lastModifiedDate,
        String owner,
        List<String> sharedWith
) {
}
