package com.example.documentstore.service;

import com.example.documentstore.model.Category;
import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentRepository;
import com.example.documentstore.repository.DocumentSearchCriteria;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every document belongs to the user who created it ({@code owner}) and may
 * additionally be shared with specific other users ({@code sharedWith}), who
 * get full access (view/download/edit/delete) but can't change who else it's
 * shared with - only the owner can. Documents with a {@code null} owner
 * (created before per-user ownership existed) are treated as visible/
 * editable by everyone rather than orphaned.
 * <p>
 * A document may be filed under one category. Documents are returned with a
 * {@code null} category both when they never had one and when theirs has
 * since been deleted.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final Set<String> configuredUsernames;
    private final CategoryService categoryService;

    public DocumentService(DocumentRepository repository, Set<String> configuredUsernames, CategoryService categoryService) {
        this.repository = repository;
        this.configuredUsernames = configuredUsernames;
        this.categoryService = categoryService;
    }

    public DocumentEntity getDocument(String id) {
        DocumentEntity document = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (!hasAccess(document, currentUsername())) {
            throw new DocumentAccessDeniedException(id);
        }
        return withoutStaleCategory(document);
    }

    public List<DocumentEntity> search(DocumentSearchCriteria criteria) {
        String username = currentUsername();
        Set<String> categoryIds = categoryService.list().stream().map(Category::id).collect(Collectors.toSet());
        return repository.search(criteria).stream()
                .filter(document -> hasAccess(document, username))
                .map(document -> document.categoryId() == null || categoryIds.contains(document.categoryId())
                        ? document
                        : document.withCategoryId(null))
                .toList();
    }

    /** A blank {@code categoryId} creates the document uncategorized. */
    public DocumentEntity createDocument(String name, String description, String categoryId, String contentType, byte[] content) {
        DocumentEntity toCreate = new DocumentEntity(
                null, name, description, content, contentType, 0, null, currentUsername(), List.of(),
                resolveCategoryId(categoryId));
        return repository.save(toCreate);
    }

    /**
     * Partial update: any {@code null} argument (other than {@code id}) keeps the
     * existing value. {@code content}/{@code contentType} are only replaced together.
     * A blank (as opposed to {@code null}) {@code categoryId} makes the document uncategorized.
     */
    public DocumentEntity updateDocument(
            String id, String name, String description, String categoryId, String contentType, byte[] content) {
        DocumentEntity existing = getDocument(id);
        DocumentEntity updated = new DocumentEntity(
                id,
                name != null ? name : existing.name(),
                description != null ? description : existing.description(),
                content != null ? content : existing.content(),
                content != null ? contentType : existing.contentType(),
                0,
                null,
                existing.owner(),
                existing.sharedWith(),
                categoryId != null ? resolveCategoryId(categoryId) : existing.categoryId()
        );
        return repository.save(updated);
    }

    public void deleteDocument(String id) {
        getDocument(id); // 404 if missing, 403 if no access
        repository.deleteById(id);
    }

    /** Only the owner may change who a document is shared with. */
    public DocumentEntity shareDocument(String id, List<String> usernames) {
        DocumentEntity existing = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        String currentUser = currentUsername();
        if (!isOwner(existing, currentUser)) {
            throw new DocumentAccessDeniedException(id);
        }

        List<String> distinctUsernames = usernames.stream().distinct().toList();
        List<String> unknown = distinctUsernames.stream().filter(u -> !configuredUsernames.contains(u)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown user(s): " + String.join(", ", unknown));
        }

        List<String> sharedWith = distinctUsernames.stream().filter(u -> !u.equals(currentUser)).toList();
        DocumentEntity updated = new DocumentEntity(
                id, existing.name(), existing.description(), existing.content(), existing.contentType(),
                0, null, existing.owner(), sharedWith, existing.categoryId());
        return withoutStaleCategory(repository.save(updated));
    }

    private String resolveCategoryId(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        if (!categoryService.exists(categoryId)) {
            throw new IllegalArgumentException("Unknown category: " + categoryId);
        }
        return categoryId;
    }

    private DocumentEntity withoutStaleCategory(DocumentEntity document) {
        if (document.categoryId() == null || categoryService.exists(document.categoryId())) {
            return document;
        }
        return document.withCategoryId(null);
    }

    private boolean isOwner(DocumentEntity document, String username) {
        return document.owner() == null || document.owner().equals(username);
    }

    private boolean hasAccess(DocumentEntity document, String username) {
        return isOwner(document, username) || document.sharedWith().contains(username);
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
