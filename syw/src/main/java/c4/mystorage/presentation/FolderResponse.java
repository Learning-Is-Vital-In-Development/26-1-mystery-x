package c4.mystorage.presentation;

import c4.mystorage.domain.StorageItem;

import java.time.LocalDateTime;

public record FolderResponse(Long id, String name, Long parentId, LocalDateTime createdAt) {
    public static FolderResponse from(StorageItem item) {
        return new FolderResponse(
                item.getId(),
                item.getDisplayName(),
                item.getParentId(),
                item.getCreatedAt()
        );
    }
}
