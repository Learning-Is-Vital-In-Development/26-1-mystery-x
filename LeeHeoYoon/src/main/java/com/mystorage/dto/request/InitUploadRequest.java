package com.mystorage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record InitUploadRequest(
    @NotBlank String fileName,
    @Positive long fileSize,
    String contentType,
    Long folderId
) {}
