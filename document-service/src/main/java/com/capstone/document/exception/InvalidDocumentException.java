package com.capstone.document.exception;

public class InvalidDocumentException extends RuntimeException {
    
    public InvalidDocumentException(
            String message) {

        super(message);
    }
}
