package c4.mystorage;

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
