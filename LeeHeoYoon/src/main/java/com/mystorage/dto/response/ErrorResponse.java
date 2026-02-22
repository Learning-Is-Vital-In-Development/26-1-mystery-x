package com.mystorage.dto.response;

public record ErrorResponse(int status, String error, String message) {}
