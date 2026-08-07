package com.example.documentstore.web;

import com.example.documentstore.model.DocumentEntity;
import com.example.documentstore.repository.DocumentSearchCriteria;
import com.example.documentstore.service.DocumentService;
import com.example.documentstore.web.dto.DocumentSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    /** 1. GET a single document's raw content by id. Metadata travels in response headers. */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getById(@PathVariable("id") String id) {
        DocumentEntity doc = service.getDocument(id);
        MediaType mediaType = doc.contentType() != null
                ? MediaType.parseMediaType(doc.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(doc.name()))
                .header("X-Document-Id", doc.id())
                .header("X-Document-Name", urlEncode(doc.name()))
                .header("X-Document-Last-Modified", doc.lastModifiedDate().toString())
                .body(doc.content());
    }

    /** 2. GET search by date range and name/description substring match. */
    @GetMapping
    public List<DocumentSummaryResponse> search(
            @RequestParam(value = "nameLike", required = false) String nameLike,
            @RequestParam(value = "descriptionLike", required = false) String descriptionLike,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        DocumentSearchCriteria criteria = new DocumentSearchCriteria(nameLike, descriptionLike, dateFrom, dateTo);
        return service.search(criteria).stream()
                .map(DocumentSummaryResponse::from)
                .toList();
    }

    /** 3. PUT inserts a new document (server generates the id). */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSummaryResponse> create(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        DocumentEntity created = service.createDocument(name, description, file.getContentType(), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/documents/" + created.id()))
                .body(DocumentSummaryResponse.from(created));
    }

    /** 4. POST updates an already-existing document (partial update, 404 if the id is unknown). */
    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResponse update(
            @PathVariable("id") String id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws IOException {
        boolean hasNewContent = file != null && !file.isEmpty();
        byte[] content = hasNewContent ? file.getBytes() : null;
        String contentType = hasNewContent ? file.getContentType() : null;

        DocumentEntity updated = service.updateDocument(id, name, description, contentType, content);
        return DocumentSummaryResponse.from(updated);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String contentDisposition(String filename) {
        String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_");
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + urlEncode(filename);
    }
}
