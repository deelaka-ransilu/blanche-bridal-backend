package com.blanchebridal.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    // NEW: covers BadCredentialsException and any other Spring Security auth failure
    // (wrong password, disabled account, etc.) — was previously falling into the
    // generic Exception handler below and returning 500 instead of 401.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildError("UNAUTHORIZED", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    // NEW: covers AccessDeniedException and its subclass AuthorizationDeniedException
    // (thrown by @PreAuthorize / hasRole checks) — was previously falling into the
    // generic Exception handler below and returning 500 instead of 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildError("FORBIDDEN", "You do not have permission to perform this action", HttpStatus.FORBIDDEN);
    }

    // MethodArgumentNotValidException — NEW (flat shape, matches buildError)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("error", "VALIDATION_ERROR");
        response.put("fields", fields);
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

    // IllegalStateException — NEW (just delegate to buildError, same as the others)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildError("BUSINESS_RULE_VIOLATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map<String, Object>> buildError(
            String code, String message, HttpStatus status) {

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", code);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return buildError("BAD_REQUEST",
                "Invalid value for parameter: " + ex.getName(), HttpStatus.BAD_REQUEST);
    }
}