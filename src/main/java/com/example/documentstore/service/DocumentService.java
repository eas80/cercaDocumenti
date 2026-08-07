package com.example.documentstore.service;

import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentRepository;
import com.example.documentstore.repository.DocumentSearchCriteria;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository repository;

    public DocumentService(DocumentRepository repository) {
        this.repository = repository;
    }

    public DocumentEntity getDocument(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public List<DocumentEntity> search(DocumentSearchCriteria criteria) {
        return repository.search(criteria);
    }

    public DocumentEntity createDocument(String name, String description, String contentType, byte[] content) {
        DocumentEntity toCreate = new DocumentEntity(null, name, description, content, contentType, 0, null);
        return repository.save(toCreate);
    }

    /**
     * Partial update: any {@code null} argument (other than {@code id}) keeps the
     * existing value. {@code content}/{@code contentType} are only replaced together.
     */
    public DocumentEntity updateDocument(String id, String name, String description, String contentType, byte[] content) {
        DocumentEntity existing = getDocument(id);
        DocumentEntity updated = new DocumentEntity(
                id,
                name != null ? name : existing.name(),
                description != null ? description : existing.description(),
                content != null ? content : existing.content(),
                content != null ? contentType : existing.contentType(),
                0,
                null
        );
        return repository.save(updated);
    }
}
