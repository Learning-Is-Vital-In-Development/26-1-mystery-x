package c4.mystorage.application;

import c4.mystorage.common.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileManager {
    private final String baseDir;

    public FileManager(@Value("${storage.base-dir}") String baseDir) {
        this.baseDir = baseDir;
    }

    public void save(InputStream inputStream, String storedName) {
        try (inputStream) {
            Path filePath = resolvePath(storedName);
            Files.createDirectories(filePath.getParent());
            Files.copy(inputStream, filePath);
        } catch (IOException e) {
            throw new StorageException("Disk 저장 실패", e);
        }
    }

    public Path resolvePath(String storedName) {
        String firstTwoChars = storedName.substring(0, 2);
        String secondTwoChars = storedName.substring(2, 4);
        return Path.of(baseDir, firstTwoChars, secondTwoChars, storedName);
    }

    public void delete(String storedName) {
        try {
            Path filePath = resolvePath(storedName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new StorageException("Disk 삭제 실패", e);
        }
    }
}
