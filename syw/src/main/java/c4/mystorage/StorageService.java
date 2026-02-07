package c4.mystorage;

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
        String firstTwoChars = storedName.substring(0, 2);
        String secondTwoChars = storedName.substring(2, 4);
        Path path = Path.of(baseDir, firstTwoChars, secondTwoChars);

        try (InputStream inputStream = storageItemCreate.content()) {
            Files.createDirectories(path);
            Files.copy(inputStream, path.resolve(storedName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
}
