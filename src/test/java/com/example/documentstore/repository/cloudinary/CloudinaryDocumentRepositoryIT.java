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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hits the real Cloudinary API - only runs when real credentials are present
 * in the environment (never committed anywhere). Skipped by default, e.g. in
 * a clean checkout or CI without those env vars set.
 */
@EnabledIfEnvironmentVariable(named = "DOCUMENTSTORE_CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryDocumentRepositoryIT {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private CloudinaryDocumentRepository repository;
    private Cloudinary rawCloudinaryClientForCleanup;

    @BeforeEach
    void setUp(@TempDir Path metadataDir) {
        cloudName = System.getenv("DOCUMENTSTORE_CLOUDINARY_CLOUD_NAME");
        apiKey = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_KEY");
        apiSecret = System.getenv("DOCUMENTSTORE_CLOUDINARY_API_SECRET");

        repository = newRepository(metadataDir);
        rawCloudinaryClientForCleanup = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
    }

    private CloudinaryDocumentRepository newRepository(Path metadataDir) {
        return new CloudinaryDocumentRepository(
                cloudName, apiKey, apiSecret, metadataDir.toString(), new ObjectMapper().findAndRegisterModules());
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

    @Test
    void deleteRemovesBothTheLocalMetadataAndTheCloudinaryAsset() throws Exception {
        DocumentEntity created = repository.save(new DocumentEntity(
                null, "To delete", "will be removed",
                "bye".getBytes(StandardCharsets.UTF_8), "text/plain", 0, null));
        String id = created.id();

        repository.deleteById(id);
        createdId = null; // already deleted - @AfterEach destroy() would just be a harmless no-op otherwise

        assertThat(repository.existsById(id)).isFalse();
        assertThat(repository.findById(id)).isEmpty();

        assertThatThrownBy(() -> rawCloudinaryClientForCleanup.api().resource(
                "documentstore/" + id, ObjectUtils.asMap("resource_type", "raw")))
                .isInstanceOf(com.cloudinary.api.exceptions.NotFound.class);
    }

    @Test
    void rebuildsTheLocalIndexFromCloudinaryWhenItsWipedLikeAfterARedeploy(@TempDir Path freshMetadataDir) throws Exception {
        // Simulates exactly the reported bug: content survives a redeploy on
        // Cloudinary, but the local metadata directory (no persistent disk
        // on the platform) is empty again, as if freshly deployed.
        DocumentEntity created = repository.save(new DocumentEntity(
                null, "Sopravvive al redeploy", "conterrà la parola chiave gennaio",
                "contenuto persistente".getBytes(StandardCharsets.UTF_8), "text/plain", 0, null));
        createdId = created.id();

        // Cloudinary's Search API (used for reconciliation) indexes newly
        // uploaded resources with a short delay - confirmed empirically,
        // this isn't a guess. In real usage a redeploy happens well after an
        // upload, so this never matters there; only this fast test needs to
        // account for it, by retrying instead of asserting immediately.
        CloudinaryDocumentRepository afterRedeploy = pollUntilReconciled(freshMetadataDir, created.id());

        Optional<DocumentEntity> found = afterRedeploy.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Sopravvive al redeploy");
        assertThat(found.get().description()).isEqualTo("conterrà la parola chiave gennaio");
        assertThat(found.get().contentType()).isEqualTo("text/plain");
        assertThat(found.get().content()).isEqualTo("contenuto persistente".getBytes(StandardCharsets.UTF_8));

        List<DocumentEntity> byDescription = afterRedeploy.search(
                new DocumentSearchCriteria(null, "gennaio", null, null));
        assertThat(byDescription).extracting(DocumentEntity::id).contains(created.id());
    }

    /**
     * Reconciliation only runs from the ApplicationReadyEvent listener in real
     * usage (kept off the startup path so it never delays other endpoints -
     * see the class javadoc), so a plain {@code new CloudinaryDocumentRepository(...)}
     * here does not auto-reconcile. Trigger it explicitly and poll, since it
     * both runs on a background thread and Cloudinary's Search index has a
     * short delay after an upload (confirmed empirically).
     */
    private CloudinaryDocumentRepository pollUntilReconciled(Path metadataDir, String id) throws InterruptedException {
        CloudinaryDocumentRepository candidate = newRepository(metadataDir);
        long deadline = System.currentTimeMillis() + 20_000;
        do {
            candidate.reconcileOnStartup();
            Thread.sleep(1_000);
            if (candidate.existsById(id)) {
                return candidate;
            }
        } while (System.currentTimeMillis() < deadline);
        return candidate;
    }
}
