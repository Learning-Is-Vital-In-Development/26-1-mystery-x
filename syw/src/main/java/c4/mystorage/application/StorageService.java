package c4.mystorage.application;

import c4.mystorage.common.StorageException;
import c4.mystorage.common.UuidGenerator;
import c4.mystorage.domain.ItemType;
import c4.mystorage.domain.StorageItem;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class StorageService {
    private final UuidGenerator uuidGenerator;
    private final StorageItemRepository repository;
    private final FileManager fileManager;

    public StorageService(UuidGenerator uuidGenerator,
                          StorageItemRepository repository,
                          FileManager fileManager) {
        this.uuidGenerator = uuidGenerator;
        this.repository = repository;
        this.fileManager = fileManager;
    }

    public StorageItem createFolder(Long ownerId, String name, Long parentId) {
        if (parentId != null) {
            validateParentFolder(ownerId, parentId);
        }
        validateNoDuplicateFolder(ownerId, parentId, name);

        StorageItem folder = new StorageItem(
                parentId,
                ownerId,
                name,
                null,
                0L,
                ItemType.DIRECTORY,
                null,
                null
        );
        return repository.save(folder);
    }

    private void validateParentFolder(Long ownerId, Long parentId) {
        StorageItem parent = repository.findByIdAndItemTypeAndDeletedAtIsNull(parentId, ItemType.DIRECTORY.name())
                .orElseThrow(() -> new StorageException("부모 폴더를 찾을 수 없습니다: " + parentId));
        checkOwnership(ownerId, parent);
    }

    private void validateNoDuplicateFolder(Long ownerId, Long parentId, String name) {
        if (repository.existsByOwnerAndParentAndNameAndType(
                ownerId, parentId, name, ItemType.DIRECTORY.name())) {
            throw new StorageException("이미 존재하는 폴더입니다: " + name);
        }
    }

    public void renameFolder(Long ownerId, Long folderId, String newName) {
        StorageItem folder = repository.findByIdAndItemTypeAndDeletedAtIsNull(folderId, ItemType.DIRECTORY.name())
                .orElseThrow(() -> new StorageException("폴더를 찾을 수 없습니다: " + folderId));

        checkOwnership(ownerId, folder);

        if (!folder.getDisplayName().equals(newName)) {
            validateNoDuplicateFolder(ownerId, folder.getParentId(), newName);
            folder.rename(newName);
            repository.save(folder);
        }
    }

    public void save(StorageItemCreate storageItemCreate) {
        String storedName = uuidGenerator.generate().toString();
        fileManager.save(storageItemCreate.content(), storedName);

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

    public StorageFileData getFile(Long ownerId, String storedName) {
        StorageItem storageItem = getByStoredName(storedName);
        checkOwnership(ownerId, storageItem);

        Path filePath = fileManager.resolvePath(storedName);
        return new StorageFileData(
                filePath.toFile(),
                storageItem.getDisplayName(),
                storageItem.getContentType()
        );
    }

    private StorageItem getByStoredName(String storedName) {
        return repository.findByStoredNameAndDeletedAtIsNull(storedName)
                .orElseThrow(() -> new StorageException("저장된 파일이 없습니다: " + storedName));
    }

    public void delete(Long ownerId, String storedName) {
        StorageItem storageItem = getByStoredName(storedName);
        checkOwnership(ownerId, storageItem);

        fileManager.delete(storedName);

        storageItem.delete();
        repository.save(storageItem);
    }

    private void checkOwnership(Long ownerId, StorageItem storageItem) {
        if (storageItem.isNotOwnedBy(ownerId)) {
            throw new StorageException("접근 권한이 없습니다. ownerId: " + ownerId);
        }
    }
}
