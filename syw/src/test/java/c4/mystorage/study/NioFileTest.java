package c4.mystorage.study;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Java NIO File API 학습 테스트
 * Path, Files, FileSystem 등 NIO.2 파일 관련 API 학습
 */
class NioFileTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Path API")
    class PathApi {

        @Test
        @DisplayName("Path.of()로 경로 생성")
        void createPathWithOf() {
            Path path = Path.of("/Users", "test", "file.txt");

            assertThat(path.toString()).isEqualTo("/Users/test/file.txt");
            System.out.println("Path: " + path);
        }

        @Test
        @DisplayName("Path 구성요소 분석")
        void pathComponents() {
            Path path = Path.of("/Users/test/documents/file.txt");

            assertThat(path.getFileName().toString()).isEqualTo("file.txt");
            assertThat(path.getParent().toString()).isEqualTo("/Users/test/documents");
            assertThat(path.getRoot().toString()).isEqualTo("/");
            assertThat(path.getNameCount()).isEqualTo(4);  // Users, test, documents, file.txt

            System.out.println("파일명: " + path.getFileName());
            System.out.println("부모 경로: " + path.getParent());
            System.out.println("루트: " + path.getRoot());
            System.out.println("경로 요소 개수: " + path.getNameCount());
        }

        @Test
        @DisplayName("상대 경로와 절대 경로")
        void relativeAndAbsolutePath() {
            Path relativePath = Path.of("src/main/java");
            Path absolutePath = Path.of("/Users/test/project");

            assertThat(relativePath.isAbsolute()).isFalse();
            assertThat(absolutePath.isAbsolute()).isTrue();

            // 상대 경로를 절대 경로로 변환 - user.dir 기준으로 변환됨
            System.out.println("user.dir 시스템 속성: " + System.getProperty("user.dir"));
            Path resolved = relativePath.toAbsolutePath();
            assertThat(resolved.toString()).startsWith(System.getProperty("user.dir"));
            assertThat(resolved.isAbsolute()).isTrue();

            System.out.println("상대 경로: " + relativePath + " (절대 경로 여부: " + relativePath.isAbsolute() + ")");
            System.out.println("상대 경로의 절대 경로: " + resolved);
            System.out.println("절대 경로: " + absolutePath + " (절대 경로 여부: " + absolutePath.isAbsolute() + ")");
        }

        @Test
        @DisplayName("resolve()로 경로 결합")
        void resolvePath() {
            Path basePath = Path.of("/Users/test");
            Path resolved = basePath.resolve("documents/file.txt");

            assertThat(resolved.toString()).isEqualTo("/Users/test/documents/file.txt");

            // 절대 경로를 resolve하면 절대 경로가 그대로 반환됨
            Path absoluteChild = Path.of("/other/path");
            Path resolvedAbsolute = basePath.resolve(absoluteChild);
            assertThat(resolvedAbsolute.toString()).isEqualTo("/other/path");

            System.out.println("기본 경로: " + basePath);
            System.out.println("결합된 경로: " + resolved);
            System.out.println("절대 경로 resolve: " + resolvedAbsolute);
        }

        @Test
        @DisplayName("relativize()로 상대 경로 계산")
        void relativizePath() {
            Path base = Path.of("/Users/test");
            Path target = Path.of("/Users/test/documents/file.txt");

            Path relative = base.relativize(target);

            assertThat(relative.toString()).isEqualTo("documents/file.txt");

            System.out.println("기준 경로: " + base);
            System.out.println("대상 경로: " + target);
            System.out.println("상대 경로: " + relative);
        }

        @Test
        @DisplayName("normalize()로 경로 정규화")
        void normalizePath() {
            Path path = Path.of("/Users/test/../test/./documents/file.txt");
            Path normalized = path.normalize();

            assertThat(normalized.toString()).isEqualTo("/Users/test/documents/file.txt");

            System.out.println("원본 경로: " + path);
            System.out.println("정규화된 경로: " + normalized);
        }

        @Test
        @DisplayName("sibling()으로 형제 경로 생성")
        void siblingPath() {
            Path file = Path.of("/Users/test/file1.txt");
            Path sibling = file.resolveSibling("file2.txt");

            assertThat(sibling.toString()).isEqualTo("/Users/test/file2.txt");

            System.out.println("원본 파일: " + file);
            System.out.println("형제 파일: " + sibling);
        }

        @Test
        @DisplayName("Path 요소 순회")
        void iteratePath() {
            Path path = Path.of("/Users/test/documents/file.txt");
            List<String> elements = new ArrayList<>();

            for (Path element : path) {
                elements.add(element.toString());
            }

            assertThat(elements).containsExactly("Users", "test", "documents", "file.txt");

            System.out.println("경로 요소들: " + elements);
        }
    }

    @Nested
    @DisplayName("Files - 파일 생성/삭제")
    class FilesCreateDelete {

        @Test
        @DisplayName("파일 생성")
        void createFile() throws IOException {
            Path newFile = tempDir.resolve("newfile.txt");

            Path created = Files.createFile(newFile);

            assertThat(Files.exists(created)).isTrue();
            assertThat(Files.isRegularFile(created)).isTrue();

            System.out.println("생성된 파일: " + created);
        }

        @Test
        @DisplayName("이미 존재하는 파일 생성 시 예외")
        void createExistingFile() throws IOException {
            Path existingFile = tempDir.resolve("existing.txt");
            Files.createFile(existingFile);

            assertThatThrownBy(() -> Files.createFile(existingFile))
                    .isInstanceOf(FileAlreadyExistsException.class);
        }

        @Test
        @DisplayName("디렉토리 생성")
        void createDirectory() throws IOException {
            Path newDir = tempDir.resolve("newdir");

            Path created = Files.createDirectory(newDir);

            assertThat(Files.exists(created)).isTrue();
            assertThat(Files.isDirectory(created)).isTrue();

            System.out.println("생성된 디렉토리: " + created);
        }

        @Test
        @DisplayName("중첩 디렉토리 생성 (createDirectories)")
        void createDirectories() throws IOException {
            Path nestedDir = tempDir.resolve("parent/child/grandchild");

            Path created = Files.createDirectories(nestedDir);

            assertThat(Files.exists(created)).isTrue();
            assertThat(Files.isDirectory(created)).isTrue();

            System.out.println("생성된 중첩 디렉토리: " + created);
        }

        @Test
        @DisplayName("파일 삭제")
        void deleteFile() throws IOException {
            Path file = tempDir.resolve("todelete.txt");
            Files.createFile(file);

            Files.delete(file);

            assertThat(Files.exists(file)).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 파일 삭제 시 예외")
        void deleteNonExistingFile() {
            Path nonExisting = tempDir.resolve("nonexisting.txt");

            assertThatThrownBy(() -> Files.delete(nonExisting))
                    .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("deleteIfExists()로 안전하게 삭제")
        void deleteIfExists() throws IOException {
            Path nonExisting = tempDir.resolve("nonexisting.txt");

            boolean deleted = Files.deleteIfExists(nonExisting);

            assertThat(deleted).isFalse();  // 파일이 없으므로 false
        }

        @Test
        @DisplayName("InputStream으로 파일 생성 (Files.copy)")
        void createFileFromInputStream() throws IOException {
            Path file = tempDir.resolve("from-stream.txt");
            byte[] content = "InputStream으로 작성된 내용".getBytes(StandardCharsets.UTF_8);

            try (InputStream in = new ByteArrayInputStream(content)) {
                Files.copy(in, file);
            }

            assertThat(Files.exists(file)).isTrue();
            assertThat(Files.readString(file)).isEqualTo("InputStream으로 작성된 내용");
        }

        @Test
        @DisplayName("임시 파일 생성")
        void createTempFile() throws IOException {
            Path tempFile = Files.createTempFile(tempDir, "prefix_", "_suffix.txt");

            assertThat(Files.exists(tempFile)).isTrue();
            assertThat(tempFile.getFileName().toString()).startsWith("prefix_");
            assertThat(tempFile.getFileName().toString()).endsWith("_suffix.txt");

            System.out.println("임시 파일: " + tempFile);
        }

        @Test
        @DisplayName("임시 디렉토리 생성")
        void createTempDirectory() throws IOException {
            Path tempSubDir = Files.createTempDirectory(tempDir, "tempdir_");

            assertThat(Files.exists(tempSubDir)).isTrue();
            assertThat(Files.isDirectory(tempSubDir)).isTrue();

            System.out.println("임시 디렉토리: " + tempSubDir);
        }
    }

    @Nested
    @DisplayName("Files - 파일 읽기/쓰기")
    class FilesReadWrite {

        @Test
        @DisplayName("writeString()으로 문자열 쓰기")
        void writeString() throws IOException {
            Path file = tempDir.resolve("write.txt");
            String content = "Hello, NIO!";

            Files.writeString(file, content);

            assertThat(Files.exists(file)).isTrue();
            assertThat(Files.readString(file)).isEqualTo(content);

            System.out.println("작성된 내용: " + content);
        }

        @Test
        @DisplayName("readString()으로 문자열 읽기")
        void readString() throws IOException {
            Path file = tempDir.resolve("read.txt");
            String content = "안녕하세요, NIO!";
            Files.writeString(file, content);

            String read = Files.readString(file, StandardCharsets.UTF_8);

            assertThat(read).isEqualTo(content);
            System.out.println("읽은 내용: " + read);
        }

        @Test
        @DisplayName("write()로 바이트 배열 쓰기")
        void writeBytes() throws IOException {
            Path file = tempDir.resolve("bytes.bin");
            byte[] data = {0x48, 0x65, 0x6C, 0x6C, 0x6F};  // "Hello"

            Files.write(file, data);

            byte[] read = Files.readAllBytes(file);
            assertThat(read).isEqualTo(data);
        }

        @Test
        @DisplayName("readAllLines()로 라인 단위 읽기")
        void readAllLines() throws IOException {
            Path file = tempDir.resolve("lines.txt");
            Files.writeString(file, "Line 1\nLine 2\nLine 3");

            List<String> lines = Files.readAllLines(file);

            assertThat(lines).containsExactly("Line 1", "Line 2", "Line 3");
            System.out.println("읽은 라인들: " + lines);
        }

        @Test
        @DisplayName("write()로 라인 단위 쓰기")
        void writeLines() throws IOException {
            Path file = tempDir.resolve("writelines.txt");
            List<String> lines = List.of("첫 번째 줄", "두 번째 줄", "세 번째 줄");

            Files.write(file, lines, StandardCharsets.UTF_8);

            List<String> read = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertThat(read).isEqualTo(lines);
        }

        @Test
        @DisplayName("APPEND 옵션으로 파일 추가 쓰기")
        void appendToFile() throws IOException {
            Path file = tempDir.resolve("append.txt");
            Files.writeString(file, "First\n");

            Files.writeString(file, "Second\n", StandardOpenOption.APPEND);

            String content = Files.readString(file);
            assertThat(content).isEqualTo("First\nSecond\n");

            System.out.println("최종 내용:\n" + content);
        }

        @Test
        @DisplayName("lines()로 Stream 기반 읽기 (lazy)")
        void readLinesAsStream() throws IOException {
            Path file = tempDir.resolve("stream.txt");
            Files.writeString(file, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5");

            // try-with-resources로 Stream 닫기
            try (Stream<String> lines = Files.lines(file)) {
                long count = lines
                        .filter(line -> line.contains("Line"))
                        .count();

                assertThat(count).isEqualTo(5);
            }
        }
    }

    @Nested
    @DisplayName("Files - 복사/이동")
    class FilesCopyMove {

        @Test
        @DisplayName("파일 복사")
        void copyFile() throws IOException {
            Path source = tempDir.resolve("source.txt");
            Path target = tempDir.resolve("target.txt");
            Files.writeString(source, "복사할 내용");

            Files.copy(source, target);

            assertThat(Files.exists(target)).isTrue();
            assertThat(Files.readString(target)).isEqualTo("복사할 내용");

            System.out.println("원본: " + source);
            System.out.println("복사본: " + target);
        }

        @Test
        @DisplayName("REPLACE_EXISTING 옵션으로 덮어쓰기")
        void copyWithReplace() throws IOException {
            Path source = tempDir.resolve("source.txt");
            Path target = tempDir.resolve("target.txt");
            Files.writeString(source, "새로운 내용");
            Files.writeString(target, "기존 내용");

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            assertThat(Files.readString(target)).isEqualTo("새로운 내용");
        }

        @Test
        @DisplayName("파일 이동 (rename)")
        void moveFile() throws IOException {
            Path source = tempDir.resolve("original.txt");
            Path target = tempDir.resolve("renamed.txt");
            Files.writeString(source, "이동할 내용");

            Files.move(source, target);

            assertThat(Files.exists(source)).isFalse();
            assertThat(Files.exists(target)).isTrue();
            assertThat(Files.readString(target)).isEqualTo("이동할 내용");
        }

        @Test
        @DisplayName("ATOMIC_MOVE 옵션")
        void atomicMove() throws IOException {
            Path source = tempDir.resolve("atomic_source.txt");
            Path target = tempDir.resolve("atomic_target.txt");
            Files.writeString(source, "원자적 이동");

            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);

            assertThat(Files.exists(target)).isTrue();
        }
    }

    @Nested
    @DisplayName("Files - 파일 속성")
    class FilesAttributes {

        @Test
        @DisplayName("파일 크기 확인")
        void fileSize() throws IOException {
            Path file = tempDir.resolve("size.txt");
            String content = "Hello, World!";
            Files.writeString(file, content);

            long size = Files.size(file);

            assertThat(size).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
            System.out.println("파일 크기: " + size + " bytes");
        }

        @Test
        @DisplayName("파일 존재 여부 확인")
        void exists() throws IOException {
            Path existing = tempDir.resolve("existing.txt");
            Path nonExisting = tempDir.resolve("nonexisting.txt");
            Files.createFile(existing);

            assertThat(Files.exists(existing)).isTrue();
            assertThat(Files.exists(nonExisting)).isFalse();
            assertThat(Files.notExists(nonExisting)).isTrue();
        }

        @Test
        @DisplayName("파일 타입 확인")
        void fileType() throws IOException {
            Path file = tempDir.resolve("file.txt");
            Path dir = tempDir.resolve("directory");
            Files.createFile(file);
            Files.createDirectory(dir);

            assertThat(Files.isRegularFile(file)).isTrue();
            assertThat(Files.isDirectory(dir)).isTrue();
            assertThat(Files.isDirectory(file)).isFalse();

            System.out.println(file + " is regular file: " + Files.isRegularFile(file));
            System.out.println(dir + " is directory: " + Files.isDirectory(dir));
        }

        @Test
        @DisplayName("읽기/쓰기 권한 확인")
        void permissions() throws IOException {
            Path file = tempDir.resolve("permissions.txt");
            Files.createFile(file);

            assertThat(Files.isReadable(file)).isTrue();
            assertThat(Files.isWritable(file)).isTrue();

            System.out.println("읽기 가능: " + Files.isReadable(file));
            System.out.println("쓰기 가능: " + Files.isWritable(file));
        }

        @Test
        @DisplayName("BasicFileAttributes로 상세 속성 읽기")
        void basicAttributes() throws IOException {
            Path file = tempDir.resolve("attrs.txt");
            Files.writeString(file, "attribute test");

            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

            assertThat(attrs.isRegularFile()).isTrue();
            assertThat(attrs.isDirectory()).isFalse();
            assertThat(attrs.size()).isGreaterThan(0);

            System.out.println("생성 시간: " + attrs.creationTime());
            System.out.println("마지막 수정 시간: " + attrs.lastModifiedTime());
            System.out.println("마지막 접근 시간: " + attrs.lastAccessTime());
            System.out.println("파일 크기: " + attrs.size());
        }

        @Test
        @DisplayName("마지막 수정 시간 변경")
        void setLastModifiedTime() throws IOException {
            Path file = tempDir.resolve("modified.txt");
            Files.createFile(file);

            FileTime newTime = FileTime.from(Instant.parse("2024-01-01T00:00:00Z"));
            Files.setLastModifiedTime(file, newTime);

            FileTime readTime = Files.getLastModifiedTime(file);
            assertThat(readTime).isEqualTo(newTime);

            System.out.println("설정된 수정 시간: " + readTime);
        }
    }

    @Nested
    @DisplayName("Files - 디렉토리 순회")
    class FilesDirectoryTraversal {

        @BeforeEach
        void setUpDirectoryStructure() throws IOException {
            // 테스트용 디렉토리 구조 생성
            Files.createDirectories(tempDir.resolve("dir1/subdir1"));
            Files.createDirectories(tempDir.resolve("dir1/subdir2"));
            Files.createDirectories(tempDir.resolve("dir2"));
            Files.createFile(tempDir.resolve("file1.txt"));
            Files.createFile(tempDir.resolve("file2.txt"));
            Files.createFile(tempDir.resolve("dir1/file3.txt"));
            Files.createFile(tempDir.resolve("dir1/subdir1/file4.txt"));
        }

        @Test
        @DisplayName("list()로 디렉토리 내용 나열 (1단계)")
        void listDirectory() throws IOException {
            try (Stream<Path> paths = Files.list(tempDir)) {
                List<String> names = paths
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .sorted()
                        .toList();

                assertThat(names).contains("dir1", "dir2", "file1.txt", "file2.txt");
                System.out.println("디렉토리 내용: " + names);
            }
        }

        @Test
        @DisplayName("walk()로 재귀적 순회")
        void walkDirectory() throws IOException {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                List<String> allPaths = paths
                        .map(p -> tempDir.relativize(p).toString())
                        .filter(s -> !s.isEmpty())
                        .sorted()
                        .toList();

                System.out.println("전체 경로:");
                allPaths.forEach(p -> System.out.println("  " + p));

                assertThat(allPaths).contains(
                        "dir1",
                        "dir1/subdir1",
                        "dir1/subdir1/file4.txt"
                );
            }
        }

        @Test
        @DisplayName("walk()에 depth 제한")
        void walkWithMaxDepth() throws IOException {
            try (Stream<Path> paths = Files.walk(tempDir, 1)) {  // maxDepth = 1
                long count = paths.count();

                // tempDir 자신 + 직접 하위 항목들만 (dir1, dir2, file1.txt, file2.txt)
                assertThat(count).isEqualTo(5);
            }
        }

        @Test
        @DisplayName("find()로 조건에 맞는 파일 검색")
        void findFiles() throws IOException {
            try (Stream<Path> paths = Files.find(tempDir, Integer.MAX_VALUE,
                    (path, attrs) -> attrs.isRegularFile() && path.toString().endsWith(".txt"))) {

                List<String> txtFiles = paths
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .sorted()
                        .toList();

                assertThat(txtFiles).containsExactly("file1.txt", "file2.txt", "file3.txt", "file4.txt");
                System.out.println("찾은 txt 파일들: " + txtFiles);
            }
        }

        @Test
        @DisplayName("DirectoryStream으로 디렉토리 순회")
        void directoryStream() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "*.txt")) {
                List<String> txtFiles = new ArrayList<>();

                for (Path path : stream) {
                    txtFiles.add(path.getFileName().toString());
                }

                assertThat(txtFiles).containsExactlyInAnyOrder("file1.txt", "file2.txt");
                System.out.println("DirectoryStream으로 찾은 txt 파일: " + txtFiles);
            }
        }

        @Test
        @DisplayName("walkFileTree()로 FileVisitor 사용")
        void walkFileTree() throws IOException {
            List<String> visitedFiles = new ArrayList<>();
            List<String> visitedDirs = new ArrayList<>();

            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    visitedFiles.add(tempDir.relativize(file).toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(tempDir)) {
                        visitedDirs.add(tempDir.relativize(dir).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("방문한 파일들: " + visitedFiles);
            System.out.println("방문한 디렉토리들: " + visitedDirs);

            assertThat(visitedFiles).hasSize(4);
            assertThat(visitedDirs).contains("dir1", "dir2");
        }
    }

    @Nested
    @DisplayName("FileSystem API")
    class FileSystemApi {

        @Test
        @DisplayName("기본 FileSystem 정보")
        void defaultFileSystem() {
            FileSystem fs = FileSystems.getDefault();

            System.out.println("파일 시스템: " + fs);
            System.out.println("경로 구분자: " + fs.getSeparator());
            System.out.println("읽기 전용: " + fs.isReadOnly());
            System.out.println("열림 상태: " + fs.isOpen());

            assertThat(fs.isOpen()).isTrue();
            assertThat(fs.getSeparator()).isIn("/", "\\");
        }

        @Test
        @DisplayName("루트 디렉토리 목록")
        void rootDirectories() {
            FileSystem fs = FileSystems.getDefault();
            List<Path> roots = new ArrayList<>();

            for (Path root : fs.getRootDirectories()) {
                roots.add(root);
            }

            assertThat(roots).isNotEmpty();
            System.out.println("루트 디렉토리들: " + roots);
        }

        @Test
        @DisplayName("PathMatcher로 glob 패턴 매칭")
        void pathMatcher() {
            FileSystem fs = FileSystems.getDefault();
            var matcher = fs.getPathMatcher("glob:*.txt");

            Path txtFile = Path.of("document.txt");
            Path javaFile = Path.of("Main.java");
            Path nested = Path.of("dir/file.txt");

            assertThat(matcher.matches(txtFile)).isTrue();
            assertThat(matcher.matches(javaFile)).isFalse();
            // glob:*.txt는 디렉토리 구분자를 포함하지 않으므로 nested는 매치하지 않음
            assertThat(matcher.matches(nested.getFileName())).isTrue();

            System.out.println("document.txt 매치: " + matcher.matches(txtFile));
            System.out.println("Main.java 매치: " + matcher.matches(javaFile));
        }

        @Test
        @DisplayName("glob 패턴 - 재귀적 매칭")
        void recursiveGlobPattern() {
            FileSystem fs = FileSystems.getDefault();
            var matcher = fs.getPathMatcher("glob:**/*.java");

            Path file1 = Path.of("src/main/java/Main.java");
            Path file2 = Path.of("Test.java");
            Path file3 = Path.of("src/main/resources/config.xml");

            assertThat(matcher.matches(file1)).isTrue();
            // ** 패턴은 하나 이상의 디렉토리를 의미하므로 루트의 파일은 매치하지 않음
            assertThat(matcher.matches(file2)).isFalse();
            assertThat(matcher.matches(file3)).isFalse();
        }

        @Test
        @DisplayName("regex PathMatcher")
        void regexPathMatcher() {
            FileSystem fs = FileSystems.getDefault();
            var matcher = fs.getPathMatcher("regex:.*\\.(?:txt|md)$");

            assertThat(matcher.matches(Path.of("readme.txt"))).isTrue();
            assertThat(matcher.matches(Path.of("readme.md"))).isTrue();
            assertThat(matcher.matches(Path.of("readme.java"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Files - 심볼릭 링크")
    class SymbolicLinks {

        @Test
        @DisplayName("심볼릭 링크 생성 및 확인")
        void createSymbolicLink() throws IOException {
            Path target = tempDir.resolve("target.txt");
            Path link = tempDir.resolve("link.txt");
            Files.writeString(target, "target content");

            try {
                Files.createSymbolicLink(link, target);

                assertThat(Files.isSymbolicLink(link)).isTrue();
                assertThat(Files.readSymbolicLink(link)).isEqualTo(target);
                assertThat(Files.readString(link)).isEqualTo("target content");

                System.out.println("심볼릭 링크: " + link + " -> " + target);
            } catch (UnsupportedOperationException | IOException e) {
                System.out.println("심볼릭 링크 생성 불가: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Files - 기타 유틸리티")
    class FilesUtilities {

        @Test
        @DisplayName("isSameFile()로 동일 파일 확인")
        void isSameFile() throws IOException {
            Path file = tempDir.resolve("same.txt");
            Files.createFile(file);
            Path samePath = tempDir.resolve("./same.txt");

            boolean same = Files.isSameFile(file, samePath.normalize());

            assertThat(same).isTrue();
        }

        @Test
        @DisplayName("probeContentType()으로 MIME 타입 확인")
        void probeContentType() throws IOException {
            Path txtFile = tempDir.resolve("test.txt");
            Path htmlFile = tempDir.resolve("test.html");
            Files.createFile(txtFile);
            Files.createFile(htmlFile);

            String txtType = Files.probeContentType(txtFile);
            String htmlType = Files.probeContentType(htmlFile);

            System.out.println("txt MIME 타입: " + txtType);
            System.out.println("html MIME 타입: " + htmlType);

            // 시스템에 따라 null일 수 있음
            if (txtType != null) {
                assertThat(txtType).contains("text");
            }
        }

        @Test
        @DisplayName("mismatch()로 파일 내용 비교")
        void mismatch() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Path file3 = tempDir.resolve("file3.txt");

            Files.writeString(file1, "Hello World");
            Files.writeString(file2, "Hello World");
            Files.writeString(file3, "Hello Java");

            long mismatch12 = Files.mismatch(file1, file2);
            long mismatch13 = Files.mismatch(file1, file3);

            assertThat(mismatch12).isEqualTo(-1);  // 동일한 내용
            assertThat(mismatch13).isEqualTo(6);   // 6번째 바이트부터 다름 ('W' vs 'J')

            System.out.println("file1 vs file2 mismatch 위치: " + mismatch12);
            System.out.println("file1 vs file3 mismatch 위치: " + mismatch13);
        }
    }
}
