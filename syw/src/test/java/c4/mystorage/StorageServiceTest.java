package c4.mystorage;

import c4.mystorage.application.StorageItemCreate;
import c4.mystorage.application.StorageItemRepository;
import c4.mystorage.application.StorageService;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class StorageServiceTest {
    @Autowired
    private StorageItemRepository repository;

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
                10L,
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
                baseDir
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
                1L,
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
                baseDir
        );
        storageService.save(create);

        Iterable<StorageItem> items = repository.findAll();
        assertThat(items).hasSize(1);

        StorageItem item = items.iterator().next();
        assertAll(
                () -> assertThat(item.getParentId()).isEqualTo(1L),
                () -> assertThat(item.getOwnerId()).isEqualTo(2L),
                () -> assertThat(item.getDisplayName()).isEqualTo("photo.png"),
                () -> assertThat(item.getStoredName()).isEqualTo(storedName.toString()),
                () -> assertThat(item.getSize()).isEqualTo(content.length),
                () -> assertThat(item.getItemType()).isEqualTo(ItemType.FILE),
                () -> assertThat(item.getContentType()).isEqualTo("image/png"),
                () -> assertThat(item.getExtraMetadata()).isEqualTo("{\"origin\":\"camera\"}")
        );
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
