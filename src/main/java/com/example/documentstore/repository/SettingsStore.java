package com.example.documentstore.repository;

import java.util.Optional;

/**
 * Durable storage for the few small JSON blobs that aren't documents
 * (categories, changed passwords), keyed by a short name. Backed by the same
 * place as the active {@link DocumentRepository}, so it survives whatever
 * the documents themselves survive (notably a redeploy on a platform
 * without a persistent disk, when the backend is Cloudinary).
 */
public interface SettingsStore {

    Optional<byte[]> read(String key);

    void write(String key, byte[] content);
}
