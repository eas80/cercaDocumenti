package com.example.documentstore;

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

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DocumentControllerTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("documentstore.storage.disk.directory", () -> storageDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void insertsFindsSearchesAndUpdatesADocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.txt", MediaType.TEXT_PLAIN_VALUE, "hello world".getBytes());

        // 3. PUT inserts a new document.
        String location = mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents")
                        .file(file)
                        .param("name", "Invoice January")
                        .param("description", "Monthly invoice for January"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Invoice January"))
                .andExpect(jsonPath("$.sizeBytes").value("hello world".getBytes().length))
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        // 1. GET by id returns the raw content.
        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Document-Id", id))
                .andExpect(content().bytes("hello world".getBytes()));

        // 2. GET search matches on name/description substrings.
        mockMvc.perform(get("/api/documents")
                        .param("nameLike", "invoice")
                        .param("descriptionLike", "january"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/documents").param("nameLike", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // 4. POST updates the existing document, keeping the untouched fields.
        MockMultipartFile updatedFile = new MockMultipartFile(
                "file", "invoice-v2.txt", MediaType.TEXT_PLAIN_VALUE, "updated content".getBytes());

        mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/{id}", id)
                        .file(updatedFile)
                        .param("description", "Revised January invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invoice January"))
                .andExpect(jsonPath("$.description").value("Revised January invoice"))
                .andExpect(jsonPath("$.sizeBytes").value("updated content".getBytes().length));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().bytes("updated content".getBytes()));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
