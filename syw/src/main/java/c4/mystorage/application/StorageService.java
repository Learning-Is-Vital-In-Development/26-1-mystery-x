package c4.mystorage.application;

import c4.mystorage.common.StorageException;
import c4.mystorage.common.UuidGenerator;
import c4.mystorage.domain.ItemType;
import c4.mystorage.domain.StorageItem;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

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

        if (folder.hasDifferentName(newName)) {
            validateNoDuplicateFolder(ownerId, folder.getParentId(), newName);
            folder.rename(newName);
            repository.save(folder);
        }
    }

    public List<StorageItem> listFolder(Long ownerId, Long parentId) {
        if (parentId != null) {
            StorageItem folder = repository.findByIdAndItemTypeAndDeletedAtIsNull(
                            parentId, ItemType.DIRECTORY.name())
                    .orElseThrow(() -> new StorageException("폴더를 찾을 수 없습니다: " + parentId));
            checkOwnership(ownerId, folder);
            return repository.findByOwnerIdAndParentIdAndDeletedAtIsNull(ownerId, parentId);
        }
        return repository.findByOwnerIdAndParentIdIsNullAndDeletedAtIsNull(ownerId);
    }

    public void save(StorageItemCreate storageItemCreate) {
        if (storageItemCreate.parentId() != null) {
            validateParentFolder(storageItemCreate.ownerId(), storageItemCreate.parentId());
        }

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

        storageItem.delete();
        repository.save(storageItem);

        // 참조 카운트 확인 후 물리 파일 삭제
        long refCount = repository.countByStoredNameAndDeletedAtIsNull(storedName);
        if (refCount == 0) {
            try {
                fileManager.delete(storedName);
            } catch (Exception e) {
                // 물리 삭제 실패 시 DB 상태는 유지 (배치에서 재시도)
            }
        }
    }

    public void deleteFolder(Long ownerId, Long folderId) {
        StorageItem folder = findDirectoryById(folderId);
        checkOwnership(ownerId, folder);

        List<StorageItem> allItems = collectSubtreeItems(folder);
        allItems.forEach(StorageItem::delete);
        repository.saveAll(allItems);
    }

    /**
     * BFS로 루트 폴더 하위의 모든 항목(파일+디렉토리)을 수집한다.
     * queue에는 디렉토리만 넣어 자식 탐색 대상으로 사용하고,
     * allItems에는 파일과 디렉토리 모두 담아 삭제 대상으로 반환한다.
     * @param root
     * @return
     */
    private List<StorageItem> collectSubtreeItems(StorageItem root) {
        List<StorageItem> allItems = new ArrayList<>();
        Queue<StorageItem> queue = new LinkedList<>();
        queue.add(root);
        allItems.add(root);

        while (!queue.isEmpty()) {
            StorageItem current = queue.poll();
            List<StorageItem> children = repository.findByParentIdAndDeletedAtIsNull(current.getId());
            for (StorageItem child : children) {
                allItems.add(child);
                if (child.isDirectory()) {
                    queue.add(child);
                }
            }
        }
        return allItems;
    }

    public void moveItem(Long ownerId, Long itemId, Long targetParentId) {
        StorageItem item = findByIdAndDeletedAtIsNull(itemId);
        checkOwnership(ownerId, item);

        if (item.isSameParent(targetParentId)) {
            return;
        }

        if (targetParentId != null) {
            validateParentFolder(ownerId, targetParentId);
        }

        if (item.isDirectory()) {
            validateNotCircular(itemId, targetParentId);
        }

        validateNoDuplicate(ownerId, targetParentId, item.getDisplayName());

        item.moveTo(targetParentId);
        repository.save(item);
    }

    private void validateNotCircular(Long sourceId, Long targetParentId) {
        Long currentId = targetParentId;
        while (currentId != null) {
            if (currentId.equals(sourceId)) {
                throw new StorageException("순환 참조가 발생합니다.");
            }
            currentId = repository.findParentIdById(currentId).orElse(null);
        }
    }

    private void validateNoDuplicate(Long ownerId, Long targetParentId, String displayName) {
        if (repository.existsByOwnerAndParentAndName(ownerId, targetParentId, displayName)) {
            throw new StorageException("동일한 이름이 이미 존재합니다: " + displayName);
        }
    }

    public StorageItem copyItem(Long ownerId, Long itemId, Long targetParentId) {
        StorageItem item = findByIdAndDeletedAtIsNull(itemId);
        checkOwnership(ownerId, item);

        if (targetParentId != null) {
            validateParentFolder(ownerId, targetParentId);
        }

        validateNoDuplicate(ownerId, targetParentId, item.getDisplayName());

        if (item.isFile()) {
            return copyFile(item, targetParentId);
        }
        return copyFolder(item, targetParentId);
    }

    private StorageItem copyFile(StorageItem source, Long targetParentId) {
        StorageItem copy = new StorageItem(
                targetParentId,
                source.getOwnerId(),
                source.getDisplayName(),
                source.getStoredName(),  // 같은 stored_name 공유
                source.getSize(),
                ItemType.FILE,
                source.getContentType(),
                source.getExtraMetadata()
        );
        return repository.save(copy);
    }

    private StorageItem copyFolder(StorageItem sourceFolder, Long targetParentId) {
        StorageItem newFolder = new StorageItem(
                targetParentId,
                sourceFolder.getOwnerId(),
                sourceFolder.getDisplayName(),
                null,
                0L,
                ItemType.DIRECTORY,
                null,
                null
        );
        newFolder = repository.save(newFolder);

        List<StorageItem> children = repository.findByParentIdAndDeletedAtIsNull(sourceFolder.getId());
        for (StorageItem child : children) {
            if (child.isFile()) {
                copyFile(child, newFolder.getId());
            } else {
                copyFolder(child, newFolder.getId());
            }
        }

        return newFolder;
    }

    private StorageItem findDirectoryById(Long folderId) {
        return repository.findByIdAndItemTypeAndDeletedAtIsNull(folderId, ItemType.DIRECTORY.name())
                .orElseThrow(() -> new StorageException("폴더를 찾을 수 없습니다: " + folderId));
    }

    private StorageItem findByIdAndDeletedAtIsNull(Long itemId) {
        return repository.findByIdAndDeletedAtIsNull(itemId)
                .orElseThrow(() -> new StorageException("항목을 찾을 수 없습니다: " + itemId));
    }

    private void checkOwnership(Long ownerId, StorageItem storageItem) {
        if (storageItem.isNotOwnedBy(ownerId)) {
            throw new StorageException("접근 권한이 없습니다. ownerId: " + ownerId);
        }
    }
}
