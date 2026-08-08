package com.example.documentstore.repository.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentSearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real Cloudinary API - only runs when real credentials are present
 * in the environment (never committed anywhere). Skipped by default, e.g. in
 * a clean checkout or CI without those env vars set.
 */
@EnabledIfEnvironmentVariable(named = "DOCUMENTSTORE_CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryDocumentRepositoryIT {

    private CloudinaryDocumentRepository repository;
    private Cloudinary rawCloudinaryClientForCleanup;

    @BeforeEach
    void setUp(@TempDir Path metadataDir) {
        String cloudName = System.getenv("DOCUMENTSTORE_CLOUDINARY_CLOUD_NAME");
        String apiKey = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_KEY");
        String apiSecret = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_SECRET");

        repository = new CloudinaryDocumentRepository(
                cloudName, apiKey, apiSecret, metadataDir.toString(), new ObjectMapper().findAndRegisterModules());
        rawCloudinaryClientForCleanup = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
    }

    private String createdId;

    @AfterEach
    void cleanUpUploadedTestAsset() throws Exception {
        if (createdId != null) {
            rawCloudinaryClientForCleanup.uploader().destroy(
                    "documentstore/" + createdId, ObjectUtils.asMap("resource_type", "raw"));
        }
    }

    @Test
    void savesUploadsAndRetrievesRealContentFromCloudinary() {
        byte[] originalContent = "hello from an integration test".getBytes(StandardCharsets.UTF_8);
        DocumentEntity toCreate = new DocumentEntity(
                null, "Test Cloudinary Integration", "descrizione con parola chiave fattura",
                originalContent, "text/plain", 0, null);

        DocumentEntity created = repository.save(toCreate);
        createdId = created.id();

        assertThat(created.id()).isNotBlank();
        assertThat(created.sizeBytes()).isEqualTo(originalContent.length);

        Optional<DocumentEntity> found = repository.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo(originalContent);
        assertThat(found.get().name()).isEqualTo("Test Cloudinary Integration");

        List<DocumentEntity> byName = repository.search(
                new DocumentSearchCriteria("cloudinary integration", null, null, null));
        assertThat(byName).extracting(DocumentEntity::id).contains(created.id());

        List<DocumentEntity> byDescription = repository.search(
                new DocumentSearchCriteria(null, "fattura", null, null));
        assertThat(byDescription).extracting(DocumentEntity::id).contains(created.id());

        List<DocumentEntity> noMatch = repository.search(
                new DocumentSearchCriteria("does-not-exist-xyz", null, null, null));
        assertThat(noMatch).extracting(DocumentEntity::id).doesNotContain(created.id());

        assertThat(repository.existsById(created.id())).isTrue();
        assertThat(repository.existsById("does-not-exist")).isFalse();
    }

    @Test
    void updatingReplacesTheContentOnCloudinary() {
        DocumentEntity created = repository.save(new DocumentEntity(
                null, "Versioned doc", "v1", "version one".getBytes(StandardCharsets.UTF_8), "text/plain", 0, null));
        createdId = created.id();

        DocumentEntity updated = repository.save(new DocumentEntity(
                created.id(), "Versioned doc", "v2", "version two".getBytes(StandardCharsets.UTF_8), "text/plain", 0, null));

        Optional<DocumentEntity> found = repository.findById(updated.id());
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("version two".getBytes(StandardCharsets.UTF_8));
    }
}
