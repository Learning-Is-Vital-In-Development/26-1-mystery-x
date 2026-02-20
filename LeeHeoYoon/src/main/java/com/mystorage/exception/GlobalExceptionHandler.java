package com.mystorage.exception;

import com.mystorage.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(StorageFileNotFoundException e) {
        return ResponseEntity.status(404).body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFolderNotFound(FolderNotFoundException e) {
        return ResponseEntity.status(404).body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StorageAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(StorageAccessDeniedException e) {
        return ResponseEntity.status(403).body(new ErrorResponse(403, "FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateNameException e) {
        return ResponseEntity.status(409).body(new ErrorResponse(409, "CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(StorageIOException.class)
    public ResponseEntity<ErrorResponse> handleStorageIO(StorageIOException e) {
        log.error("Storage I/O error", e);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "STORAGE_ERROR", "File storage operation failed"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(400).body(new ErrorResponse(400, "VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(400).body(new ErrorResponse(400, "BAD_REQUEST", "Missing header: X-User-Id"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(new ErrorResponse(413, "PAYLOAD_TOO_LARGE", "File exceeds 100MB"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new ErrorResponse(409, "CONFLICT", "Duplicate or constraint violation"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
