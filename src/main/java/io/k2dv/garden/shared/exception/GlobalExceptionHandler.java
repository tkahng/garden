package io.k2dv.garden.shared.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .error("BAD_REQUEST")
                        .message("Malformed or unreadable request body")
                        .status(400)
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .error("VALIDATION_ERROR")
                        .message(message)
                        .status(400)
                        .build());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.builder()
                        .error(ex.getErrorCode())
                        .message(ex.getMessage())
                        .status(ex.getStatus().value())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.builder()
                        .error("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred")
                        .status(500)
                        .build());
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private final String error;
        private final String message;
        private final int status;
    }
}
