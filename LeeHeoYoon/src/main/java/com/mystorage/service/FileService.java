package com.mystorage.service;

import com.mystorage.domain.FileMetadata;
import com.mystorage.domain.Folder;
import com.mystorage.domain.UploadStatus;
import com.mystorage.dto.response.FileResponse;
import com.mystorage.exception.DuplicateNameException;
import com.mystorage.exception.FolderNotFoundException;
import com.mystorage.exception.StorageFileNotFoundException;
import com.mystorage.exception.StorageIOException;
import com.mystorage.repository.FileMetadataRepository;
import com.mystorage.repository.FolderRepository;
import com.mystorage.storage.PhysicalStorageManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final Executor fileWriteExecutor;
    private final FileMetadataRepository fileRepo;
    private final FolderRepository folderRepo;
    private final PhysicalStorageManager storageManager;
    private final TransactionTemplate txTemplate;

    @Transactional
    public FileResponse upload(Long userId, MultipartFile file, Long folderId) {
        // 1. 폴더 검증 (null이면 루트)
        String folderPath = null;
        if (folderId != null) {
            Folder folder = getOwnedFolder(userId, folderId);
            folderPath = folder.getPath();
        }

        // ★ v4: 파일명 sanitize ★
        String originalName = sanitizeFilename(file.getOriginalFilename());

        // 2. 중복 파일명 체크
        if (fileRepo.existsByUserIdAndFolderIdAndOriginalNameAndDeletedFalse(
                userId, folderId, originalName)) {
            throw new DuplicateNameException(
                "File '" + originalName + "' already exists in this folder");
        }

        // ★ v4: content-type 검증 ★
        String contentType = resolveContentType(file);

        // 3. UUID 생성
        UUID storedName = UUID.randomUUID();

        // 4. 메타데이터 즉시 저장 (PENDING)
        FileMetadata metadata = FileMetadata.builder()
            .userId(userId)
            .originalName(originalName)
            .storedName(storedName)
            .folderId(folderId)
            .folderPath(folderPath)
            .fileSize(file.getSize())
            .contentType(contentType)
            .uploadStatus(UploadStatus.PENDING)
            .build();
        fileRepo.save(metadata);

        // 5. MultipartFile을 temp 파일로 전송
        Path tempFile;
        try {
            tempFile = storageManager.createTempFile();
            // ★ IMPORTANT: transferTo(File)을 사용할 것. transferTo(Path) 사용 금지. ★
            // transferTo(File) → Part.write() → DiskFileItem.write() → renameTo() (즉시)
            // transferTo(Path) → FileCopyUtils.copy() → 바이트 단위 복사 (느림)
            file.transferTo(tempFile.toFile());
        } catch (IOException e) {
            fileRepo.updateUploadStatus(metadata.getId(), UploadStatus.FAILED);
            throw new StorageIOException("Failed to save temp file", e);
        }

        // 6. 트랜잭션 커밋 후 비동기로 최종 위치로 이동 (virtual thread)
        //    ★ afterCommit(): 메타데이터가 DB에 확정된 후에만 async 작업 시작 ★
        final Long metadataId = metadata.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        storageManager.moveFromTemp(tempFile, storedName);
                        updateStatusSafely(metadataId, UploadStatus.COMPLETED);
                    } catch (Exception e) {
                        log.error("Async file write failed for {}", storedName, e);
                        storageManager.deleteTempQuietly(tempFile);
                        updateStatusSafely(metadataId, UploadStatus.FAILED);
                    }
                }, fileWriteExecutor);
            }
        });

        // 7. 즉시 반환 (202 Accepted)
        return FileResponse.from(metadata);
    }

    @Transactional(readOnly = true)
    public FileMetadata getFileForDownload(Long userId, Long fileId) {
        FileMetadata file = getOwnedFile(userId, fileId);

        // ★ PENDING/FAILED 파일 다운로드 차단 ★
        if (file.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new StorageFileNotFoundException(
                "File is not available yet (status: " + file.getUploadStatus() + ")");
        }
        return file;
    }

    @Transactional(readOnly = true)
    public FileResponse getMetadata(Long userId, Long fileId) {
        return FileResponse.from(getOwnedFile(userId, fileId));
    }

    @Transactional
    public void softDelete(Long userId, Long fileId) {
        FileMetadata file = getOwnedFile(userId, fileId);
        file.setDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        fileRepo.save(file);
    }

    @Transactional
    public FileResponse moveFile(Long userId, Long fileId, Long targetFolderId) {
        FileMetadata file = getOwnedFile(userId, fileId);

        // ★ 이동 대상 폴더에 동일 이름 파일 존재 여부 체크 ★
        if (fileRepo.existsByUserIdAndFolderIdAndOriginalNameAndDeletedFalse(
                userId, targetFolderId, file.getOriginalName())) {
            throw new DuplicateNameException(
                "File '" + file.getOriginalName() + "' already exists in target folder");
        }

        String newFolderPath = null;
        if (targetFolderId != null) {
            Folder targetFolder = getOwnedFolder(userId, targetFolderId);
            newFolderPath = targetFolder.getPath();
        }

        file.setFolderId(targetFolderId);
        file.setFolderPath(newFolderPath);
        fileRepo.save(file);
        return FileResponse.from(file);
    }

    @Transactional
    public FileResponse copyFile(Long userId, Long fileId, Long targetFolderId) {
        FileMetadata source = getOwnedFile(userId, fileId);

        if (source.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new StorageFileNotFoundException("Cannot copy incomplete file");
        }

        // Check for duplicate filename in target folder
        if (fileRepo.existsByUserIdAndFolderIdAndOriginalNameAndDeletedFalse(
                userId, targetFolderId, source.getOriginalName())) {
            throw new DuplicateNameException(
                "File '" + source.getOriginalName() + "' already exists in target folder");
        }

        String targetFolderPath = null;
        if (targetFolderId != null) {
            Folder targetFolder = getOwnedFolder(userId, targetFolderId);
            targetFolderPath = targetFolder.getPath();
        }

        UUID newStoredName = UUID.randomUUID();

        FileMetadata copy = FileMetadata.builder()
            .userId(userId)
            .originalName(source.getOriginalName())
            .storedName(newStoredName)
            .folderId(targetFolderId)
            .folderPath(targetFolderPath)
            .fileSize(source.getFileSize())
            .contentType(source.getContentType())
            .uploadStatus(UploadStatus.PENDING)
            .build();
        fileRepo.save(copy);

        final Long copyId = copy.getId();
        final UUID sourceStoredName = source.getStoredName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        storageManager.copyFile(sourceStoredName, newStoredName);
                        updateStatusSafely(copyId, UploadStatus.COMPLETED);
                    } catch (Exception e) {
                        log.error("Async file copy failed for {}", newStoredName, e);
                        updateStatusSafely(copyId, UploadStatus.FAILED);
                    }
                }, fileWriteExecutor);
            }
        });

        return FileResponse.from(copy);
    }

    /**
     * 파일명 sanitize: null/공백 방지, 경로 탐색 문자 제거, 길이 제한
     */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        String sanitized = original.replace("\\", "/");
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", "");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Invalid filename");
        }
        if (sanitized.length() > 500) {
            String ext = "";
            int dotIdx = sanitized.lastIndexOf('.');
            if (dotIdx > 0 && (sanitized.length() - dotIdx) < 500) {
                ext = sanitized.substring(dotIdx);
            }
            sanitized = sanitized.substring(0, 500 - ext.length()) + ext;
        }
        return sanitized;
    }

    /**
     * Content-Type 결정: 서버측 probeContentType 우선, 실패 시 클라이언트 값 사용,
     * 둘 다 없으면 application/octet-stream 기본값
     */
    private String resolveContentType(MultipartFile file) {
        // 1. 서버측 MIME 감지 (파일 확장자 기반)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            try {
                String probed = java.nio.file.Files.probeContentType(Path.of(originalFilename));
                if (probed != null) {
                    return probed;
                }
            } catch (IOException ignored) {}
        }
        // 2. 클라이언트 제공 Content-Type 폴백
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    /**
     * afterCommit() 비동기 블록에서 호출 — 별도 트랜잭션 없이 실행되므로
     * 실패 시 WARN 로그로 기록하고 예외를 삼킨다.
     */
    private void updateStatusSafely(Long metadataId, UploadStatus status) {
        try {
            txTemplate.executeWithoutResult(tx ->
                fileRepo.updateUploadStatus(metadataId, status)
            );
        } catch (Exception ex) {
            log.warn("Failed to update upload status to {} for metadata {}: {}",
                status, metadataId, ex.getMessage());
        }
    }

    private FileMetadata getOwnedFile(Long userId, Long fileId) {
        return fileRepo.findByIdAndUserIdAndDeletedFalse(fileId, userId)
            .orElseThrow(() -> new StorageFileNotFoundException("File not found: " + fileId));
    }

    private Folder getOwnedFolder(Long userId, Long folderId) {
        return folderRepo.findByIdAndUserIdAndDeletedFalse(folderId, userId)
            .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + folderId));
    }
}
