package com.mystorage.repository;

import com.mystorage.domain.FileMetadata;
import com.mystorage.domain.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
    boolean existsByUserIdAndFolderIdAndOriginalNameAndDeletedFalse(
        Long userId, Long folderId, String originalName);
    List<FileMetadata> findByUserIdAndFolderIdAndDeletedFalseAndUploadStatus(
        Long userId, Long folderId, UploadStatus uploadStatus);
    List<FileMetadata> findByUserIdAndFolderIdAndDeletedFalseAndUploadStatus(
        Long userId, Long folderId, UploadStatus uploadStatus, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FileMetadata f SET f.uploadStatus = :status WHERE f.id = :id")
    void updateUploadStatus(@Param("id") Long id, @Param("status") UploadStatus status);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE file_metadata SET deleted = TRUE, deleted_at = now()
        WHERE folder_path <@ CAST(:parentPath AS ltree) AND deleted = FALSE
        """, nativeQuery = true)
    int softDeleteFilesUnderPath(@Param("parentPath") String parentPath);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE file_metadata
        SET folder_path = CAST(:newPrefix || subpath(folder_path, nlevel(CAST(:oldPrefix AS ltree))) AS ltree)
        WHERE folder_path <@ CAST(:oldPrefix AS ltree)
        """, nativeQuery = true)
    int moveFilesUnderPath(@Param("oldPrefix") String oldPrefix,
                           @Param("newPrefix") String newPrefix);

    @Query(value = """
        SELECT stored_name FROM file_metadata
        WHERE deleted = TRUE AND deleted_at < :threshold
        """, nativeQuery = true)
    List<UUID> findStoredNamesForCleanup(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        DELETE FROM file_metadata WHERE deleted = TRUE AND deleted_at < :threshold
        """, nativeQuery = true)
    int hardDeleteOlderThan(@Param("threshold") LocalDateTime threshold);

    @Query(value = """
        SELECT * FROM file_metadata
        WHERE upload_status IN ('PENDING', 'FAILED') AND created_at < :threshold
        """, nativeQuery = true)
    List<FileMetadata> findStaleUploads(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        DELETE FROM file_metadata
        WHERE upload_status IN ('PENDING', 'FAILED') AND created_at < :threshold
        """, nativeQuery = true)
    int hardDeleteStaleUploads(@Param("threshold") LocalDateTime threshold);
}
