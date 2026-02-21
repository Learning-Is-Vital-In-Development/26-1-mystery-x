package c4.mystorage;

import c4.mystorage.application.*;
import c4.mystorage.common.StorageException;
import c4.mystorage.domain.ItemType;
import c4.mystorage.domain.StorageItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class StorageServiceTest {
    @Autowired
    private StorageItemRepository repository;
    @Autowired
    private FileManager fileManager;

    @Value("${storage.base-dir}")
    private String baseDir;

    @BeforeEach
    void setUp() throws IOException {
        repository.deleteAll();
        deleteBaseDir();
    }

    @AfterEach
    void tearDown() throws IOException {
        repository.deleteAll();
        deleteBaseDir();
    }

    @DisplayName("파일 저장 경로가 baseDir/첫2글자/두번째2글자/storedName 구조로 생성된다")
    @Test
    void savesFileUnderShardedPath() {
        UUID storedName = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] content = "hello-storage".getBytes(StandardCharsets.UTF_8);
        StorageItemCreate create = new StorageItemCreate(
                null,
                20L,
                "hello.txt",
                new ByteArrayInputStream(content),
                content.length,
                ItemType.FILE,
                "text/plain",
                "{\"k\":\"v\"}"
        );

        StorageService storageService = new StorageService(
                () -> storedName,
                repository,
                fileManager
        );
        storageService.save(create);

        String storedNameText = storedName.toString();
        String firstTwoChars = storedNameText.substring(0, 2);
        String secondTwoChars = storedNameText.substring(2, 4);
        Path expectedPath = Path.of(baseDir, firstTwoChars, secondTwoChars, storedNameText);

        assertAll(
                () -> assertThat(Files.exists(expectedPath)).isTrue(),
                () -> assertThat(Files.readAllBytes(expectedPath)).isEqualTo(content)
        );
    }

    @DisplayName("파일 저장 후 StorageItem 테이블 값이 저장 요청과 일치한다")
    @Test
    void persistsStorageItemValues() {
        UUID storedName = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        byte[] content = "data-blob".getBytes(StandardCharsets.UTF_8);

        StorageItemCreate create = new StorageItemCreate(
                null,
                2L,
                "photo.png",
                new ByteArrayInputStream(content),
                content.length,
                ItemType.FILE,
                "image/png",
                "{\"origin\":\"camera\"}"
        );

        StorageService storageService = new StorageService(
                () -> storedName,
                repository,
                fileManager
        );
        storageService.save(create);

        Iterable<StorageItem> items = repository.findAll();
        assertThat(items).hasSize(1);

        StorageItem item = items.iterator().next();
        assertAll(
                () -> assertThat(item.getParentId()).isNull(),
                () -> assertThat(item.getOwnerId()).isEqualTo(2L),
                () -> assertThat(item.getDisplayName()).isEqualTo("photo.png"),
                () -> assertThat(item.getStoredName()).isEqualTo(storedName.toString()),
                () -> assertThat(item.getSize()).isEqualTo(content.length),
                () -> assertThat(item.getItemType()).isEqualTo(ItemType.FILE),
                () -> assertThat(item.getContentType()).isEqualTo("image/png"),
                () -> assertThat(item.getExtraMetadata()).isEqualTo("{\"origin\":\"camera\"}")
        );
    }

    @DisplayName("없는 storedName을 조회하면 에러가 발생한다")
    @Test
    void getFileThrowsWhenMissing() {
        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        String storedName = "missing-stored-name";
        assertThatThrownBy(() -> storageService.getFile(1L, storedName))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("저장된 파일이 없습니다")
                .hasMessageContaining(storedName);
    }

    @DisplayName("파일은 있지만 ownerId가 다르면 에러가 발생한다")
    @Test
    void getFileThrowsWhenOwnerIdMismatch() throws IOException {
        String storedName = "abcd1234-1111-2222-3333-444444444444";
        byte[] content = "owner-mismatch".getBytes(StandardCharsets.UTF_8);
        writeFile(storedName, content);

        repository.save(new StorageItem(
                null,
                100L,
                "sample.txt",
                storedName,
                (long) content.length,
                ItemType.FILE,
                "text/plain",
                null
        ));

        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        assertThatThrownBy(() -> storageService.getFile(200L, storedName))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("접근 권한이 없습니다")
                .hasMessageContaining("ownerId: 200");
    }

    @DisplayName("ownerId가 일치하면 storedName 기반 파일을 반환한다")
    @Test
    void getFileReturnsStoredFileWhenMatch() throws IOException {
        String storedName = "beefcafe-aaaa-bbbb-cccc-ddddeeeeeeee";
        byte[] content = "stored-file".getBytes(StandardCharsets.UTF_8);
        Path expectedPath = writeFile(storedName, content);

        repository.save(new StorageItem(
                null,
                10L,
                "report.pdf",
                storedName,
                (long) content.length,
                ItemType.FILE,
                "application/pdf",
                null
        ));

        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        StorageFileData fileData = storageService.getFile(10L, storedName);
        assertAll(
                () -> assertThat(fileData.file()).isNotNull()
                        .exists()
                        .isEqualTo(expectedPath.toFile()),
                () -> assertThat(fileData.displayName()).isEqualTo("report.pdf"),
                () -> assertThat(fileData.contentType()).isEqualTo("application/pdf"),
                () -> assertThat(Files.readAllBytes(expectedPath)).isEqualTo(content)
        );
    }

    @DisplayName("파일 삭제 시 디스크에서 파일이 제거된다")
    @Test
    void deleteRemovesFileFromDisk() throws IOException {
        String storedName = "dddd1111-2222-3333-4444-555555555555";
        byte[] content = "to-delete".getBytes(StandardCharsets.UTF_8);
        Path filePath = writeFile(storedName, content);

        repository.save(new StorageItem(
                null,
                30L,
                "delete.txt",
                storedName,
                (long) content.length,
                ItemType.FILE,
                "text/plain",
                null
        ));

        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        storageService.delete(30L, storedName);

        assertThat(Files.exists(filePath)).isFalse();
    }

    @DisplayName("파일 삭제 시 StorageItem의 deletedAt에 값이 기록된다")
    @Test
    void deleteSetsDeletedAt() throws IOException {
        String storedName = "eeee1111-2222-3333-4444-666666666666";
        byte[] content = "mark-deleted".getBytes(StandardCharsets.UTF_8);
        Path filePath = writeFile(storedName, content);

        StorageItem savedItem = repository.save(new StorageItem(
                null,
                40L,
                "mark.txt",
                storedName,
                (long) content.length,
                ItemType.FILE,
                "text/plain",
                null
        ));

        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        storageService.delete(40L, storedName);

        StorageItem item = repository.findById(savedItem.getId())
                .orElseThrow();
        assertAll(
                () -> assertThat(Files.exists(filePath)).isFalse(),
                () -> assertThat(item.getDeletedAt()).isNotNull()
        );
    }

    @DisplayName("이미 삭제된 파일은 조회되지 않고 에러가 발생한다")
    @Test
    void getFileThrowsWhenAlreadyDeleted() throws IOException {
        String storedName = "ffff1111-2222-3333-4444-777777777777";
        byte[] content = "already-deleted".getBytes(StandardCharsets.UTF_8);
        writeFile(storedName, content);

        StorageItem storageItem = new StorageItem(
                null,
                50L,
                "deleted.txt",
                storedName,
                (long) content.length,
                ItemType.FILE,
                "text/plain",
                null
        );
        storageItem.delete();
        repository.save(storageItem);

        StorageService storageService = new StorageService(
                UUID::randomUUID,
                repository,
                fileManager
        );

        assertThatThrownBy(() -> storageService.getFile(50L, storedName))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("저장된 파일이 없습니다")
                .hasMessageContaining(storedName);
    }

    private StorageService newStorageService() {
        return new StorageService(UUID::randomUUID, repository, fileManager);
    }

    @DisplayName("루트 폴더를 생성하면 DIRECTORY 타입으로 저장된다")
    @Test
    void createRootFolder() {
        StorageService storageService = newStorageService();

        StorageItem folder = storageService.createFolder(1L, "documents", null);

        assertAll(
                () -> assertThat(folder.getId()).isNotNull(),
                () -> assertThat(folder.getDisplayName()).isEqualTo("documents"),
                () -> assertThat(folder.getParentId()).isNull(),
                () -> assertThat(folder.getOwnerId()).isEqualTo(1L),
                () -> assertThat(folder.getItemType()).isEqualTo(ItemType.DIRECTORY),
                () -> assertThat(folder.getSize()).isEqualTo(0L)
        );
    }

    @DisplayName("하위 폴더 생성 시 부모 폴더가 존재해야 한다")
    @Test
    void createSubFolder() {
        StorageService storageService = newStorageService();
        StorageItem parent = storageService.createFolder(1L, "documents", null);

        StorageItem child = storageService.createFolder(1L, "photos", parent.getId());

        assertAll(
                () -> assertThat(child.getParentId()).isEqualTo(parent.getId()),
                () -> assertThat(child.getDisplayName()).isEqualTo("photos")
        );
    }

    @DisplayName("존재하지 않는 부모 폴더에 하위 폴더를 생성하면 에러가 발생한다")
    @Test
    void createSubFolderFailsWhenParentMissing() {
        StorageService storageService = newStorageService();

        assertThatThrownBy(() -> storageService.createFolder(1L, "orphan", 9999L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("부모 폴더를 찾을 수 없습니다");
    }

    @DisplayName("다른 사용자의 폴더에 하위 폴더를 생성하면 에러가 발생한다")
    @Test
    void createSubFolderFailsWhenParentOwnedByOther() {
        StorageService storageService = newStorageService();
        StorageItem otherUserFolder = storageService.createFolder(1L, "private", null);

        assertThatThrownBy(() -> storageService.createFolder(2L, "hack", otherUserFolder.getId()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("접근 권한이 없습니다");
    }

    @DisplayName("같은 위치에 동일한 이름의 폴더를 중복 생성하면 에러가 발생한다")
    @Test
    void createDuplicateFolderFails() {
        StorageService storageService = newStorageService();
        storageService.createFolder(1L, "documents", null);

        assertThatThrownBy(() -> storageService.createFolder(1L, "documents", null))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("이미 존재하는 폴더입니다");
    }

    @DisplayName("서로 다른 사용자는 같은 이름의 루트 폴더를 만들 수 있다")
    @Test
    void differentUsersCanCreateSameNameFolder() {
        StorageService storageService = newStorageService();

        StorageItem folder1 = storageService.createFolder(1L, "documents", null);
        StorageItem folder2 = storageService.createFolder(2L, "documents", null);

        assertAll(
                () -> assertThat(folder1.getOwnerId()).isEqualTo(1L),
                () -> assertThat(folder2.getOwnerId()).isEqualTo(2L),
                () -> assertThat(folder1.getDisplayName()).isEqualTo(folder2.getDisplayName())
        );
    }

    @DisplayName("폴더 이름을 변경하면 변경된 이름으로 저장된다")
    @Test
    void renameFolder() {
        StorageService storageService = newStorageService();
        StorageItem folder = storageService.createFolder(1L, "old-name", null);

        storageService.renameFolder(1L, folder.getId(), "new-name");

        StorageItem renamed = repository.findById(folder.getId()).orElseThrow();
        assertThat(renamed.getDisplayName()).isEqualTo("new-name");
    }

    @DisplayName("폴더 이름 변경 시 같은 위치에 동일한 이름이 있으면 에러가 발생한다")
    @Test
    void renameFolderFailsWhenDuplicate() {
        StorageService storageService = newStorageService();
        storageService.createFolder(1L, "existing", null);
        StorageItem target = storageService.createFolder(1L, "target", null);

        assertThatThrownBy(() -> storageService.renameFolder(1L, target.getId(), "existing"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("이미 존재하는 폴더입니다");
    }

    @DisplayName("다른 사용자의 폴더 이름을 변경하면 에러가 발생한다")
    @Test
    void renameFolderFailsWhenNotOwner() {
        StorageService storageService = newStorageService();
        StorageItem folder = storageService.createFolder(1L, "private", null);

        assertThatThrownBy(() -> storageService.renameFolder(2L, folder.getId(), "hacked"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("접근 권한이 없습니다");
    }

    @DisplayName("폴더 목록 조회 시 해당 폴더의 파일과 하위 폴더가 모두 반환된다")
    @Test
    void listFolderReturnsFilesAndSubFolders() {
        StorageService storageService = newStorageService();
        StorageItem parent = storageService.createFolder(1L, "root-folder", null);

        storageService.createFolder(1L, "sub-folder", parent.getId());

        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        storageService.save(new StorageItemCreate(
                parent.getId(), 1L, "file.txt",
                new ByteArrayInputStream(content), content.length,
                ItemType.FILE, "text/plain", null
        ));

        List<StorageItem> items = storageService.listFolder(1L, parent.getId());

        assertAll(
                () -> assertThat(items).hasSize(2),
                () -> assertThat(items).extracting(StorageItem::getDisplayName)
                        .containsExactlyInAnyOrder("sub-folder", "file.txt")
        );
    }

    @DisplayName("루트 목록 조회 시 parentId가 null인 항목만 반환된다")
    @Test
    void listRootReturnsOnlyRootItems() {
        StorageService storageService = newStorageService();
        StorageItem rootFolder = storageService.createFolder(1L, "root-folder", null);
        storageService.createFolder(1L, "child", rootFolder.getId());

        List<StorageItem> items = storageService.listFolder(1L, null);

        assertAll(
                () -> assertThat(items).hasSize(1),
                () -> assertThat(items.get(0).getDisplayName()).isEqualTo("root-folder")
        );
    }

    @DisplayName("다른 사용자의 폴더 목록을 조회하면 에러가 발생한다")
    @Test
    void listFolderFailsWhenNotOwner() {
        StorageService storageService = newStorageService();
        StorageItem folder = storageService.createFolder(1L, "private", null);

        assertThatThrownBy(() -> storageService.listFolder(2L, folder.getId()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("접근 권한이 없습니다");
    }

    @DisplayName("파일 업로드 시 존재하지 않는 부모 폴더를 지정하면 에러가 발생한다")
    @Test
    void uploadFileFailsWhenParentFolderMissing() {
        StorageService storageService = newStorageService();
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);

        StorageItemCreate create = new StorageItemCreate(
                9999L, 1L, "file.txt",
                new ByteArrayInputStream(content), content.length,
                ItemType.FILE, "text/plain", null
        );

        assertThatThrownBy(() -> storageService.save(create))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("부모 폴더를 찾을 수 없습니다");
    }

    private Path writeFile(String storedName, byte[] content) throws IOException {
        String firstTwoChars = storedName.substring(0, 2);
        String secondTwoChars = storedName.substring(2, 4);
        Path dir = Path.of(baseDir, firstTwoChars, secondTwoChars);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(storedName);
        Files.write(filePath, content);
        return filePath;
    }

    private void deleteBaseDir() throws IOException {
        if (baseDir == null) {
            return;
        }
        Path dir = Path.of(baseDir);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted((left, right) -> Integer.compare(right.getNameCount(), left.getNameCount()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
