package com.cardpool.backend.controller;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;

import com.cardpool.backend.exception.CardDataUnavailableException;

import lombok.extern.slf4j.Slf4j;

/** Maps errors to HTTP statuses with a readable {@code {"error": "..."}} body. */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MultipartException.class })
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** An uploaded file that cannot be read (not an .xlsx...). */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> unreadableFile(IOException e) {
        log.warn("Unreadable input: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Could not read the file: " + e.getMessage());
    }

    @ExceptionHandler(CardDataUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(CardDataUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
