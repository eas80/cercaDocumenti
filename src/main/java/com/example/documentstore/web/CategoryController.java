package com.example.documentstore.web;

import com.example.documentstore.model.Category;
import com.example.documentstore.service.CategoryService;
import com.example.documentstore.web.dto.CategoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Category> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<Category> create(@RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request.name()));
    }

    @PutMapping("/{id}")
    public Category rename(@PathVariable("id") String id, @RequestBody CategoryRequest request) {
        return service.rename(id, request.name());
    }

    /** Documents filed under the category are kept and become uncategorized. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
