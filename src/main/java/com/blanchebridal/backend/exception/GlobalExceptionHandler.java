package com.blanchebridal.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildError("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return buildError("UNAUTHORIZED", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return buildError("CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> error = new HashMap<>();
        error.put("code", "VALIDATION_ERROR");
        error.put("message", "Validation failed");
        error.put("status", 400);
        error.put("fields", fields);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return ResponseEntity.badRequest().body(response);
    }

    // Let Spring handle its own internal exceptions (Swagger, static resources, etc.)
    // Only catch exceptions that come from YOUR /api/** controllers
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, HttpServletRequest request) throws Exception {

        String path = request.getRequestURI();

        // If it's not an API call, rethrow so Spring handles it normally
        if (!path.startsWith("/api/")) {
            throw ex;
        }

        return buildError("INTERNAL_ERROR", "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildError(
            String code, String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("status", status.value());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return ResponseEntity.status(status).body(response);
    }
}