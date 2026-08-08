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

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("documentstore.storage.disk.directory", () -> storageDir.toString());
        registry.add("documentstore.auth.users", () -> TEST_USERNAME + ":" + TEST_PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearerToken;

    @BeforeEach
    void logIn() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEST_USERNAME + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        bearerToken = "Bearer " + json.get("token").asText();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", bearerToken);
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
}
