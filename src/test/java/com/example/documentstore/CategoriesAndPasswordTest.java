package com.example.documentstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CategoriesAndPasswordTest {

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test-password";
    // Separate accounts for the password tests, so changing their password
    // can't break the login of the tests that just need a token.
    private static final String CHANGER_USERNAME = "changer";
    private static final String CHANGER_PASSWORD = "changer-password";
    private static final String STUBBORN_USERNAME = "stubborn";
    private static final String STUBBORN_PASSWORD = "stubborn-password";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("documentstore.storage.disk.directory", () -> storageDir.toString());
        registry.add("documentstore.auth.users", () -> USERNAME + ":" + PASSWORD
                + "," + CHANGER_USERNAME + ":" + CHANGER_PASSWORD
                + "," + STUBBORN_USERNAME + ":" + STUBBORN_PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Credentials(username, password))));
    }

    private String bearer(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    private String createCategory(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private record Credentials(String username, String password) {
    }

    @Test
    void categoriesRequireAToken() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsRenamesAndDeletesACategory() throws Exception {
        String token = bearer(USERNAME, PASSWORD);
        String id = createCategory(token, "Bollette");

        mockMvc.perform(get("/api/categories").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].name").value("Bollette"));

        mockMvc.perform(put("/api/categories/{id}", id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Utenze\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Utenze"));

        mockMvc.perform(delete("/api/categories/{id}", id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/categories/{id}", id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankAndDuplicateCategoryNames() throws Exception {
        String token = bearer(USERNAME, PASSWORD);
        createCategory(token, "Contratti");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"contratti\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentCategoryCanBeSetChangedClearedAndSurvivesCategoryDeletion() throws Exception {
        String token = bearer(USERNAME, PASSWORD);
        String firstCategory = createCategory(token, "Fatture");
        String secondCategory = createCategory(token, "Ricevute");
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", MediaType.TEXT_PLAIN_VALUE, "contenuto".getBytes());

        String location = mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Documento categorizzato")
                        .param("categoryId", firstCategory)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(firstCategory))
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        // Omitting categoryId on update keeps it.
        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .param("description", "nuova descrizione")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(firstCategory));

        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .param("categoryId", secondCategory)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(secondCategory));

        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .param("categoryId", "does-not-exist")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());

        // Deleting the category keeps the document, now uncategorized.
        mockMvc.perform(delete("/api/categories/{id}", secondCategory).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/documents").param("nameLike", "categorizzato").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").doesNotExist());

        // An empty categoryId explicitly clears it.
        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .param("categoryId", firstCategory)
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .param("categoryId", "")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void changedPasswordReplacesTheConfiguredOne() throws Exception {
        String token = bearer(CHANGER_USERNAME, CHANGER_PASSWORD);

        mockMvc.perform(post("/api/auth/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + CHANGER_PASSWORD + "\",\"newPassword\":\"a-brand-new-one\"}"))
                .andExpect(status().isNoContent());

        login(CHANGER_USERNAME, CHANGER_PASSWORD).andExpect(status().isUnauthorized());
        login(CHANGER_USERNAME, "a-brand-new-one").andExpect(status().isOk());
        // Other accounts are unaffected.
        login(USERNAME, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordChangeIsRejectedWithAWrongCurrentPasswordOrATooShortNewOne() throws Exception {
        String token = bearer(STUBBORN_USERNAME, STUBBORN_PASSWORD);

        mockMvc.perform(post("/api/auth/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"a-brand-new-one\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + STUBBORN_PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        login(STUBBORN_USERNAME, STUBBORN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"a-brand-new-one\"}"))
                .andExpect(status().isUnauthorized());
    }
}
