package c4.mystorage.presentation;

import c4.mystorage.domain.ItemType;
import c4.mystorage.domain.StorageItem;

import java.time.LocalDateTime;

public record StorageItemResponse(
        Long id,
        String displayName,
        Long parentId,
        Long ownerId,
        String storedName,
        Long size,
        ItemType itemType,
        String contentType,
        LocalDateTime createdAt
) {
    public static StorageItemResponse from(StorageItem item) {
        return new StorageItemResponse(
                item.getId(),
                item.getDisplayName(),
                item.getParentId(),
                item.getOwnerId(),
                item.getStoredName(),
                item.getSize(),
                item.getItemType(),
                item.getContentType(),
                item.getCreatedAt()
        );
    }
}
