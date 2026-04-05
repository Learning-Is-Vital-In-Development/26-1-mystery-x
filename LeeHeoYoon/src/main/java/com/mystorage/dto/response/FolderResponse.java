package com.mystorage.dto.response;

import com.mystorage.domain.Folder;
import java.time.LocalDateTime;

public record FolderResponse(
    Long id,
    String name,
    Long parentId,
    LocalDateTime createdAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
            folder.getId(),
            folder.getName(),
            folder.getParentId(),
            folder.getCreatedAt()
        );
    }
}
