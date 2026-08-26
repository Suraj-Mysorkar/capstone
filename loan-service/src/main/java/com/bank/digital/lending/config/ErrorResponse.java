package com.bank.digital.lending.config;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,
    LocalDateTime timestamp,
    List<String> validationErrors
) {
    public static ErrorResponse of(int status, String error, String message, String path, List<String> validationErrors) {
        return new ErrorResponse(status, error, message, path, LocalDateTime.now(), validationErrors);
    }
}
