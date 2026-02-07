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

    private Path createDirectoryPath(String storedName) {
        String firstTwoChars = storedName.substring(0, 2);
        String secondTwoChars = storedName.substring(2, 4);
        return Path.of(baseDir, firstTwoChars, secondTwoChars);
    }
}
