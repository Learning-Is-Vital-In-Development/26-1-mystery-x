package c4.mystorage;

public record StorageItemPayload(
        Long parentId,
        String filename,
        ItemType itemType,
        String extraMetadata
) {
}
