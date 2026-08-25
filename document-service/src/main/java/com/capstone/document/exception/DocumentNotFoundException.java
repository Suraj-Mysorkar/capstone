package com.capstone.document.exception;

public class DocumentNotFoundException extends RuntimeException {
    
    public DocumentNotFoundException(
            String message) {

        super(message);
    }
}
