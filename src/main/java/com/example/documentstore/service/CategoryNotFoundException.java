package com.example.documentstore.service;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String id) {
        super("Category not found: " + id);
    }
}
