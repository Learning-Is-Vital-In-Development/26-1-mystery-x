package c4.mystorage.presentation;

import c4.mystorage.domain.ItemType;

public record StorageItemPayload(
        Long parentId,
        String filename,
        ItemType itemType,
        String extraMetadata
) {
}
