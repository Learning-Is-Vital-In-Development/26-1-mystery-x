package com.mystorage.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class PhysicalStorageManager {

    private final Path baseDir;
    private final Path tempDir;

    public PhysicalStorageManager(StorageProperties props) throws IOException {
        this.baseDir = Path.of(props.getBasePath());
        this.tempDir = baseDir.resolve(".tmp");
        Files.createDirectories(baseDir);
        Files.createDirectories(tempDir);
    }

    public Path createTempFile() throws IOException {
        return Files.createTempFile(tempDir, "upload-", ".tmp");
    }

    public void moveFromTemp(Path tempFile, UUID storedName) throws IOException {
        Path target = baseDir.resolve(storedName.toString());
        Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
    }

    public void deleteTempQuietly(Path tempFile) {
        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
    }

    public boolean delete(UUID storedName) throws IOException {
        return Files.deleteIfExists(baseDir.resolve(storedName.toString()));
    }

    public boolean exists(UUID storedName) {
        return Files.exists(baseDir.resolve(storedName.toString()));
    }

    public void copyFile(UUID source, UUID target) throws IOException {
        Path sourcePath = baseDir.resolve(source.toString());
        Path targetPath = baseDir.resolve(target.toString());
        Files.copy(sourcePath, targetPath);
    }
}
