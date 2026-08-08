package com.example.documentstore.repository;

import com.example.documentstore.model.DocumentEntity;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for documents. Today it is implemented by
 * {@link com.example.documentstore.repository.disk.DiskDocumentRepository}
 * (files on disk). To move to MongoDB, add a new implementation backed by
 * Spring Data MongoDB and activate it via {@code documentstore.storage.type} —
 * no changes are needed in {@link com.example.documentstore.service.DocumentService}
 * or the controller. See README.md for the concrete migration steps.
 */
public interface DocumentRepository {

    Optional<DocumentEntity> findById(String id);

    List<DocumentEntity> search(DocumentSearchCriteria criteria);

    /**
     * Inserts (id == null) or fully replaces (id != null) a document,
     * assigning a fresh {@code lastModifiedDate}.
     */
    DocumentEntity save(DocumentEntity document);

    boolean existsById(String id);

    /** No-op if the document doesn't exist - callers that need a 404 check {@link #existsById} first. */
    void deleteById(String id);
}
