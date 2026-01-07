package com.raj.springmarketanalysis.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message
    ) {}

    @ExceptionHandler(com.raj.springmarketanalysis.api.ApiExceptions.NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(404).body(
                new ApiError(Instant.now(), 404, "Not Found", ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(Instant.now(), 400, "Bad Request", ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleState(IllegalStateException ex) {
        // You can later map specific messages to 429/502; start simple:
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiError(Instant.now(), 500, "Internal Server Error", ex.getMessage())
        );
    }

    @ExceptionHandler(com.raj.springmarketanalysis.api.ApiExceptions.TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleRateLimit(RuntimeException ex) {
        return ResponseEntity.status(429).body(
                new ApiError(Instant.now(), 429, "Too Many Requests", ex.getMessage())
        );
    }

    @ExceptionHandler(com.raj.springmarketanalysis.api.ApiExceptions.UpstreamException.class)
    public ResponseEntity<ApiError> handleUpstream(RuntimeException ex) {
        return ResponseEntity.status(502).body(
                new ApiError(Instant.now(), 502, "Bad Gateway", ex.getMessage())
        );
    }
}
