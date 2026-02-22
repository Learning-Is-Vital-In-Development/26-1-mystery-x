package c4.mystorage.application;

import java.io.File;

public record StorageFileData(
        File file,
        String displayName,
        String contentType
) {
}
