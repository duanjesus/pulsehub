package com.pulsehub.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fields
) {
    public record FieldErrorDetail(String field, String message) {
    }
}
