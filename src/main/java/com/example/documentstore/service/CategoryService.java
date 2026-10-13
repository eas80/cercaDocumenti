package com.example.documentstore.service;

import com.example.documentstore.model.Category;
import com.example.documentstore.repository.SettingsStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Categories are one flat list shared by every user (any logged-in user may
 * create/rename/delete them), persisted as a single JSON blob. Deleting a
 * category does not touch the documents filed under it: they keep the stale
 * id and are simply treated as uncategorized from then on (see {@link
 * DocumentService}).
 */
@Service
public class CategoryService {

    private static final String SETTINGS_KEY = "categories";
    private static final int MAX_NAME_LENGTH = 60;

    private final SettingsStore settingsStore;
    private final ObjectMapper objectMapper;

    public CategoryService(SettingsStore settingsStore, ObjectMapper objectMapper) {
        this.settingsStore = settingsStore;
        this.objectMapper = objectMapper;
    }

    public synchronized List<Category> list() {
        return load().stream()
                .sorted(Comparator.comparing(Category::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized boolean exists(String id) {
        return load().stream().anyMatch(c -> c.id().equals(id));
    }

    public synchronized Category create(String name) {
        List<Category> categories = load();
        Category created = new Category(UUID.randomUUID().toString(), validateName(name, null, categories));
        categories.add(created);
        store(categories);
        return created;
    }

    public synchronized Category rename(String id, String name) {
        List<Category> categories = load();
        int index = indexOf(categories, id);
        Category renamed = new Category(id, validateName(name, id, categories));
        categories.set(index, renamed);
        store(categories);
        return renamed;
    }

    public synchronized void delete(String id) {
        List<Category> categories = load();
        categories.remove(indexOf(categories, id));
        store(categories);
    }

    private static int indexOf(List<Category> categories, String id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new CategoryNotFoundException(id);
    }

    private static String validateName(String name, String ownId, List<Category> categories) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Category name is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
        boolean duplicate = categories.stream()
                .anyMatch(c -> !c.id().equals(ownId) && c.name().equalsIgnoreCase(trimmed));
        if (duplicate) {
            throw new IllegalArgumentException("A category named '" + trimmed + "' already exists");
        }
        return trimmed;
    }

    private List<Category> load() {
        return settingsStore.read(SETTINGS_KEY)
                .map(this::parse)
                .orElseGet(ArrayList::new);
    }

    private ArrayList<Category> parse(byte[] bytes) {
        try {
            return new ArrayList<>(objectMapper.readValue(bytes, new TypeReference<List<Category>>() {
            }));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse stored categories", e);
        }
    }

    private void store(List<Category> categories) {
        try {
            settingsStore.write(SETTINGS_KEY, objectMapper.writeValueAsBytes(categories));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize categories", e);
        }
    }
}
