package c4.mystorage.application;

import c4.mystorage.common.StorageException;
import c4.mystorage.common.UuidGenerator;
import c4.mystorage.domain.StorageItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class StorageService {
    private final UuidGenerator uuidGenerator;
    private final StorageItemRepository repository;
    private final String baseDir;

    public StorageService(UuidGenerator uuidGenerator,
                          StorageItemRepository repository,
                          @Value("${storage.base-dir}") String baseDir) {
        this.uuidGenerator = uuidGenerator;
        this.repository = repository;
        this.baseDir = baseDir;
    }

    public void save(StorageItemCreate storageItemCreate) {
        String storedName = uuidGenerator.generate().toString();
        saveToDisk(storageItemCreate.content(), storedName);

        StorageItem storageItem = new StorageItem(
                storageItemCreate.parentId(),
                storageItemCreate.ownerId(),
                storageItemCreate.filename(),
                storedName,
                storageItemCreate.size(),
                storageItemCreate.itemType(),
                storageItemCreate.contentType(),
                storageItemCreate.extraMetadata()
        );

        repository.save(storageItem);
    }

    private void saveToDisk(InputStream inputStream, String storedName) {
        try (inputStream) {
            Path directoryPath = createDirectoryPath(storedName);
            Files.createDirectories(directoryPath);
            Files.copy(inputStream, directoryPath.resolve(storedName));
        } catch (IOException e) {
            throw new StorageException("Disk 저장 실패", e);
        }
    }

    public StorageFileData getFile(Long ownerId, String storedName) {
        StorageItem storageItem = repository.findByStoredNameAndDeletedAtIsNull(storedName)
                .orElseThrow(() -> new StorageException("저장된 파일이 없습니다: " + storedName));
        if (storageItem.isNotOwnedBy(ownerId)) {
            throw new StorageException("접근 권한이 없습니다. ownerId: " + ownerId);
        }

        Path filePath = createDirectoryPath(storedName).resolve(storedName);
        return new StorageFileData(
                filePath.toFile(),
                storageItem.getDisplayName(),
                storageItem.getContentType()
        );
    }

    public void delete(Long ownerId, String storedName) {
        StorageItem storageItem = repository.findByStoredNameAndDeletedAtIsNull(storedName)
                .orElseThrow(() -> new StorageException("저장된 파일이 없습니다: " + storedName));
        if (storageItem.isNotOwnedBy(ownerId)) {
            throw new StorageException("접근 권한이 없습니다. ownerId: " + ownerId);
        }

        Path filePath = createDirectoryPath(storedName).resolve(storedName);
        deleteFromDisk(storedName, filePath);

        storageItem.delete();
        repository.save(storageItem);
    }

    private void deleteFromDisk(String storedName, Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new StorageException("파일 삭제 실패: " + storedName, e);
        }
    }

    private Path createDirectoryPath(String storedName) {
        String firstTwoChars = storedName.substring(0, 2);
        String secondTwoChars = storedName.substring(2, 4);
        return Path.of(baseDir, firstTwoChars, secondTwoChars);
    }
}
