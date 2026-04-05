package com.mystorage.exception;

public class StorageAccessDeniedException extends RuntimeException {
    public StorageAccessDeniedException(String message) {
        super(message);
    }
}
