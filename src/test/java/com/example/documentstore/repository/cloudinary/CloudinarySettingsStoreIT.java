package com.example.documentstore.repository.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real Cloudinary API - only runs when real credentials are present
 * in the environment, same as {@link CloudinaryDocumentRepositoryIT}.
 */
@EnabledIfEnvironmentVariable(named = "DOCUMENTSTORE_CLOUDINARY_API_SECRET", matches = ".+")
class CloudinarySettingsStoreIT {

    private final String cloudName = System.getenv("DOCUMENTSTORE_CLOUDINARY_CLOUD_NAME");
    private final String apiKey = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_KEY");
    private final String apiSecret = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_SECRET");

    private String writtenKey;

    private CloudinarySettingsStore newStore() {
        return new CloudinarySettingsStore(cloudName, apiKey, apiSecret);
    }

    @AfterEach
    void cleanUpWrittenTestAsset() throws Exception {
        if (writtenKey != null) {
            new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true))
                    .uploader().destroy(
                            "documentstore-settings/" + writtenKey + ".json", ObjectUtils.asMap("resource_type", "raw"));
        }
    }

    @Test
    void readingAKeyThatWasNeverWrittenYieldsEmpty() {
        assertThat(newStore().read("it-missing-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void aFreshProcessReadsBackTheLatestWrittenContent() {
        writtenKey = "it-roundtrip-" + UUID.randomUUID();
        CloudinarySettingsStore store = newStore();
        store.write(writtenKey, "first".getBytes(StandardCharsets.UTF_8));
        store.write(writtenKey, "second".getBytes(StandardCharsets.UTF_8));

        // A new instance has an empty cache, like the app after a redeploy.
        assertThat(newStore().read(writtenKey)).hasValue("second".getBytes(StandardCharsets.UTF_8));
    }
}
