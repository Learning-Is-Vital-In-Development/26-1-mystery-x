package c4.mystorage.application;

import c4.mystorage.domain.ItemType;

import java.io.InputStream;

public record StorageItemCreate(
        Long parentId,
        Long ownerId,
        String filename,
        InputStream content,
        long size,
        ItemType itemType,
        String contentType,
        String extraMetadata
) {
}
