package c4.mystorage.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FileManagerTest {
    private static final String BASE_DIR = "temp/test/file-manager";

    private FileManager fileManager;

    @BeforeEach
    void setUp() throws IOException {
        fileManager = new FileManager(BASE_DIR);
        deleteDir();
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDir();
    }

    @DisplayName("파일 저장 성공 시 sharded 경로에 파일이 생성된다")
    @Test
    void saveCreatesFileUnderShardedPath() {
        String storedName = "abcd1234-1111-2222-3333-444444444444";
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        fileManager.save(new ByteArrayInputStream(content), storedName);

        Path filePath = fileManager.resolvePath(storedName);
        assertThat(filePath).exists();
        assertThat(filePath.toFile()).hasContent("hello");
    }

    @DisplayName("파일 삭제 성공 시 디스크에서 파일이 제거된다")
    @Test
    void deleteRemovesFile() throws IOException {
        String storedName = "efgh5678-1111-2222-3333-444444444444";
        Path filePath = fileManager.resolvePath(storedName);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "to-delete");

        fileManager.delete(storedName);

        assertThat(filePath).doesNotExist();
    }

    @DisplayName("존재하지 않는 파일 삭제 시 예외가 발생하지 않는다")
    @Test
    void deleteNonExistentFileDoesNotThrow() {
        String storedName = "none1234-1111-2222-3333-444444444444";

        fileManager.delete(storedName);

        assertThat(fileManager.resolvePath(storedName)).doesNotExist();
    }

    @DisplayName("resolvePath는 baseDir/첫2글자/다음2글자/storedName 경로를 반환한다")
    @Test
    void resolvePathReturnsShardedPath() {
        String storedName = "efgh5678-1111-2222-3333-444444444444";

        Path path = fileManager.resolvePath(storedName);

        assertThat(path).isEqualTo(Path.of(BASE_DIR, "ef", "gh", storedName));
    }

    private void deleteDir() throws IOException {
        Path dir = Path.of(BASE_DIR);
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
