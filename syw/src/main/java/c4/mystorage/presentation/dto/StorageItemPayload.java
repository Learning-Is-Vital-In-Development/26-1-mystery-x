package c4.mystorage.presentation.dto;

import c4.mystorage.domain.ItemType;

public record StorageItemPayload(
        Long parentId,
        String filename,
        ItemType itemType,
        String extraMetadata
) {
}
