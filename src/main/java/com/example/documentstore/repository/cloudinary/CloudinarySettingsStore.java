package com.example.documentstore.repository.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import com.example.documentstore.repository.SettingsStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores each settings blob as a Cloudinary raw asset, under its own folder
 * so {@link CloudinaryDocumentRepository}'s reconciliation never mistakes
 * one for a document. Unlike document metadata there is no local-file copy:
 * the local filesystem is exactly what doesn't survive a redeploy, and these
 * blobs (changed passwords in particular) must not silently revert.
 * <p>
 * Each blob is fetched from Cloudinary once per process and then served from
 * memory - this app runs as a single instance, so nothing else can change it
 * behind our back. A failed fetch is not cached and surfaces as an error,
 * rather than being treated as "nothing stored".
 */
@Component
@ConditionalOnProperty(prefix = "documentstore.storage", name = "type", havingValue = "cloudinary")
public class CloudinarySettingsStore implements SettingsStore {

    private static final String PUBLIC_ID_PREFIX = "documentstore-settings/";

    private final Cloudinary cloudinary;
    private final Map<String, Optional<byte[]>> cache = new ConcurrentHashMap<>();

    public CloudinarySettingsStore(
            @Value("${documentstore.storage.cloudinary.cloud-name:}") String cloudName,
            @Value("${documentstore.storage.cloudinary.api-key:}") String apiKey,
            @Value("${documentstore.storage.cloudinary.api-secret:}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    @Override
    public Optional<byte[]> read(String key) {
        Optional<byte[]> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<byte[]> fetched = fetch(key);
        cache.put(key, fetched);
        return fetched;
    }

    @Override
    public void write(String key, byte[] content) {
        try {
            cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", publicId(key),
                    "resource_type", "raw",
                    "overwrite", true,
                    "invalidate", true
            ));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload settings '" + key + "' to Cloudinary", e);
        }
        cache.put(key, Optional.of(content));
    }

    private Optional<byte[]> fetch(String key) {
        String secureUrl;
        try {
            // Looked up via the Admin API (rather than building a delivery URL
            // by hand) to get the versioned URL - a version-less one can keep
            // serving a stale copy from the CDN after an overwrite.
            ApiResponse resource = cloudinary.api().resource(publicId(key), ObjectUtils.asMap("resource_type", "raw"));
            secureUrl = (String) resource.get("secure_url");
        } catch (NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Failed to look up settings '" + key + "' on Cloudinary", e));
        }

        try (var in = URI.create(secureUrl).toURL().openStream()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download settings '" + key + "' from Cloudinary", e);
        }
    }

    private static String publicId(String key) {
        return PUBLIC_ID_PREFIX + key + ".json";
    }
}
