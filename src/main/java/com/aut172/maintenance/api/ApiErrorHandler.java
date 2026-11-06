package com.aut172.maintenance.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(new ErrorResponse(
                error.code(), error.getMessage(), error.details(), traceId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst().map(value -> value.getField() + " " + value.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "WORK_ORDER_VALIDATION_FAILED", message, Map.of(), traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                "INTERNAL_ERROR", "Unexpected server error", Map.of(), traceId(request)));
    }

    private String traceId(HttpServletRequest request) {
        Object existing = request.getAttribute("traceId");
        return existing == null ? UUID.randomUUID().toString().replace("-", "").substring(0, 16) : existing.toString();
    }

    public record ErrorResponse(String code, String message, Map<String, Object> details, String traceId) {}
}
