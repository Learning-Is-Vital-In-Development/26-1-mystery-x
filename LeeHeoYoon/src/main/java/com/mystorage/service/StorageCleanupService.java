package com.mystorage.service;

import com.mystorage.domain.FileMetadata;
import com.mystorage.domain.UploadStatus;
import com.mystorage.repository.FileMetadataRepository;
import com.mystorage.repository.FolderRepository;
import com.mystorage.storage.PhysicalStorageManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageCleanupService {

    private static final Duration DELETE_RETENTION = Duration.ofMinutes(30);
    private static final Duration STALE_UPLOAD_THRESHOLD = Duration.ofHours(1);
    // ★ hard-delete는 recovery 윈도우의 2배 이후에만 수행 → race condition 방지 ★
    private static final Duration STALE_HARD_DELETE_THRESHOLD = Duration.ofHours(2);

    private final FileMetadataRepository fileRepo;
    private final FolderRepository folderRepo;
    private final PhysicalStorageManager storageManager;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupDeletedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minus(DELETE_RETENTION);

        // ★ 순서 변경: 메타데이터 먼저 수집 → DB 하드 삭제 → 물리 파일 삭제 ★
        // 크래시 시 orphan 물리 파일은 남을 수 있지만, ghost 메타데이터보다 안전
        List<UUID> storedNames = fileRepo.findStoredNamesForCleanup(threshold);

        int deletedFiles = fileRepo.hardDeleteOlderThan(threshold);
        int deletedFolders = folderRepo.hardDeleteOlderThan(threshold);

        // 물리 파일 삭제 (best-effort, DB 삭제 후)
        for (UUID name : storedNames) {
            try { storageManager.delete(name); }
            catch (IOException e) { log.warn("Failed to delete physical file: {}", name, e); }
        }

        if (deletedFiles > 0 || deletedFolders > 0) {
            log.info("Cleanup: {} files, {} folders hard-deleted", deletedFiles, deletedFolders);
        }
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupStaleUploads() {
        // ★ Recovery: STALE_UPLOAD_THRESHOLD 이후 PENDING/FAILED 레코드 중 물리 파일 존재 시 복구 ★
        LocalDateTime recoveryThreshold = LocalDateTime.now().minus(STALE_UPLOAD_THRESHOLD);
        List<FileMetadata> staleFiles = fileRepo.findStaleUploads(recoveryThreshold);
        for (FileMetadata f : staleFiles) {
            if (storageManager.exists(f.getStoredName())) {
                fileRepo.updateUploadStatus(f.getId(), UploadStatus.COMPLETED);
                log.info("Recovered stale upload: {}", f.getStoredName());
            }
        }

        // ★ Hard-delete: 2x threshold 이후에만 삭제 → recovery 윈도우와 겹치지 않음 ★
        LocalDateTime hardDeleteThreshold = LocalDateTime.now().minus(STALE_HARD_DELETE_THRESHOLD);
        int cleaned = fileRepo.hardDeleteStaleUploads(hardDeleteThreshold);
        if (cleaned > 0) {
            log.info("Cleaned {} stale upload records", cleaned);
        }
    }
}
