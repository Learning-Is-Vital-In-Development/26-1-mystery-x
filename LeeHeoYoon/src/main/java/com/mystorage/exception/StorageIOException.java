package com.mystorage.exception;

public class StorageIOException extends RuntimeException {
    public StorageIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
