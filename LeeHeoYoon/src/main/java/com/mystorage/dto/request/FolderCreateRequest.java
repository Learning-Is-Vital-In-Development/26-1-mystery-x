package com.mystorage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FolderCreateRequest(
    @NotBlank @Size(max = 255)
    @Pattern(regexp = "^[^/\\\\:*?\"<>|\\x00-\\x1f]*$",
             message = "Folder name contains invalid characters")
    String name,
    Long parentId
) {}
