package com.example.documentstore.service;

/** Thrown when the authenticated user is neither the owner of a document nor in its share list. */
public class DocumentAccessDeniedException extends RuntimeException {

    public DocumentAccessDeniedException(String id) {
        super("You do not have access to document: " + id);
    }
}
