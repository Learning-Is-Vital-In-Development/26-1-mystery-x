package com.mystorage.dto.response;

import com.mystorage.domain.FileMetadata;
import java.time.LocalDateTime;

public record FileResponse(
    Long id,
    String originalName,
    long fileSize,
    String contentType,
    Long folderId,
    String uploadStatus,
    LocalDateTime createdAt
) {
    public static FileResponse from(FileMetadata file) {
        return new FileResponse(
            file.getId(),
            file.getOriginalName(),
            file.getFileSize(),
            file.getContentType(),
            file.getFolderId(),
            file.getUploadStatus().name(),
            file.getCreatedAt()
        );
    }
}
