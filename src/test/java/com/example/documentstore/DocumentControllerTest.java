package com.example.documentstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DocumentControllerTest {

    private static final String TEST_USERNAME = "tester";
    private static final String TEST_PASSWORD = "test-password";
    private static final String OTHER_USERNAME = "other";
    private static final String OTHER_PASSWORD = "other-password";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("documentstore.storage.disk.directory", () -> storageDir.toString());
        registry.add("documentstore.auth.users", () ->
                TEST_USERNAME + ":" + TEST_PASSWORD + "," + OTHER_USERNAME + ":" + OTHER_PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearerToken;
    private String otherBearerToken;

    @BeforeEach
    void logIn() throws Exception {
        bearerToken = "Bearer " + loginAndGetToken(TEST_USERNAME, TEST_PASSWORD);
        otherBearerToken = "Bearer " + loginAndGetToken(OTHER_USERNAME, OTHER_PASSWORD);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", bearerToken);
    }

    private MockHttpServletRequestBuilder asOther(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", otherBearerToken);
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEST_USERNAME + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentApiRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insertsFindsSearchesAndUpdatesADocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.txt", MediaType.TEXT_PLAIN_VALUE, "hello world".getBytes());

        // 3. PUT inserts a new document.
        String location = mockMvc.perform(authorized(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Invoice January")
                        .param("description", "Monthly invoice for January")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Invoice January"))
                .andExpect(jsonPath("$.sizeBytes").value("hello world".getBytes().length))
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        // 1. GET by id returns the raw content.
        mockMvc.perform(authorized(get("/api/documents/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Document-Id", id))
                .andExpect(content().bytes("hello world".getBytes()));

        // 2. GET search matches on name/description substrings.
        mockMvc.perform(authorized(get("/api/documents")
                        .param("nameLike", "invoice")
                        .param("descriptionLike", "january")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(authorized(get("/api/documents").param("nameLike", "does-not-exist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // 4. POST updates the existing document, keeping the untouched fields.
        MockMultipartFile updatedFile = new MockMultipartFile(
                "file", "invoice-v2.txt", MediaType.TEXT_PLAIN_VALUE, "updated content".getBytes());

        mockMvc.perform(authorized(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .file(updatedFile)
                        .param("description", "Revised January invoice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invoice January"))
                .andExpect(jsonPath("$.description").value("Revised January invoice"))
                .andExpect(jsonPath("$.sizeBytes").value("updated content".getBytes().length));

        mockMvc.perform(authorized(get("/api/documents/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("updated content".getBytes()));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
        mockMvc.perform(authorized(get("/api/documents/{id}", "does-not-exist")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesADocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "to-delete.txt", MediaType.TEXT_PLAIN_VALUE, "delete me".getBytes());

        String location = mockMvc.perform(authorized(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Da eliminare")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(authorized(delete("/api/documents/{id}", id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/documents/{id}", id)))
                .andExpect(status().isNotFound());

        mockMvc.perform(authorized(get("/api/documents").param("nameLike", "eliminare")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingAnUnknownIdReturns404() throws Exception {
        mockMvc.perform(authorized(delete("/api/documents/{id}", "does-not-exist")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", "some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentsAreIsolatedBetweenUsersUntilShared() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.txt", MediaType.TEXT_PLAIN_VALUE, "segreto".getBytes());

        String location = mockMvc.perform(authorized(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Documento privato")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        // The owner sees it.
        mockMvc.perform(authorized(get("/api/documents").param("nameLike", "privato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Another user does not - neither in search nor by direct id.
        mockMvc.perform(asOther(get("/api/documents").param("nameLike", "privato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(asOther(get("/api/documents/{id}", id)))
                .andExpect(status().isForbidden());
        mockMvc.perform(asOther(delete("/api/documents/{id}", id)))
                .andExpect(status().isForbidden());

        // Only the owner can share it - the other user trying is forbidden too.
        mockMvc.perform(asOther(post("/api/documents/{id}/share", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernames\":[\"" + OTHER_USERNAME + "\"]}"))
                .andExpect(status().isForbidden());

        // The owner shares it with the other user.
        mockMvc.perform(authorized(post("/api/documents/{id}/share", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernames\":[\"" + OTHER_USERNAME + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedWith[0]").value(OTHER_USERNAME));

        // Now the other user has full access: sees it, can download, update and delete it.
        mockMvc.perform(asOther(get("/api/documents").param("nameLike", "privato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(asOther(get("/api/documents/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("segreto".getBytes()));
        mockMvc.perform(asOther(delete("/api/documents/{id}", id)))
                .andExpect(status().isNoContent());
    }

    @Test
    void sharingWithAnUnknownUserIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", MediaType.TEXT_PLAIN_VALUE, "contenuto".getBytes());

        String location = mockMvc.perform(authorized(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Documento")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(authorized(post("/api/documents/{id}/share", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernames\":[\"does-not-exist\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsConfiguredUsernames() throws Exception {
        mockMvc.perform(authorized(get("/api/auth/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.containsInAnyOrder(TEST_USERNAME, OTHER_USERNAME)));
    }
}
