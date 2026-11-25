package ru.tmobile.generator.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GenerationAlreadyStartedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyStarted(GenerationAlreadyStartedException ex) {
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(GenerationNotStartedException.class)
    public ResponseEntity<ErrorResponse> handleNotStarted(GenerationNotStartedException ex) {
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var invalidFields = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> java.util.Map.of("field", e.getField(), "reason", e.getDefaultMessage()))
            .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Validation error", java.util.Map.of("invalid_fields", invalidFields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("ResourceType")) {
            return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid resource_type. Allowed values: TRAFFIC, MINUTES",
                java.util.Map.of("invalid_fields", java.util.List.of(
                    java.util.Map.of("field", "resource_type", "reason", "Allowed values: TRAFFIC, MINUTES"))));
        }
        if (message.contains("GenerationMode")) {
            return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid mode. Allowed values: SIMPLE, REALISTIC",
                java.util.Map.of("invalid_fields", java.util.List.of(
                    java.util.Map.of("field", "mode", "reason", "Allowed values: SIMPLE, REALISTIC"))));
        }
        if (message.contains("ProfileType")) {
            return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid profile_type. Allowed values: STANDARD, TRIP",
                java.util.Map.of("invalid_fields", java.util.List.of(
                    java.util.Map.of("field", "profile_type", "reason", "Allowed values: STANDARD, TRIP"))));
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Invalid request body: " + message,
            java.util.Map.of("raw_message", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: " + ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorCode, String message, Object details) {
        ErrorResponse error = new ErrorResponse(
            errorCode,
            message,
            Instant.now().toString(),
            java.util.UUID.randomUUID().toString(),
            details
        );
        return new ResponseEntity<>(error, status);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        String errorCode = status == HttpStatus.INTERNAL_SERVER_ERROR ? "INTERNAL_ERROR" : "VALIDATION_ERROR";
        return buildResponse(status, errorCode, message, null);
    }

    public record ErrorResponse(
        String error_code,
        String message,
        String timestamp,
        String trace_id,
        Object details
    ) {}
}
