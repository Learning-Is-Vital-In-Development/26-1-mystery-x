package com.mystorage.repository;

import com.mystorage.domain.Folder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
    boolean existsByUserIdAndParentIdAndNameAndDeletedFalse(Long userId, Long parentId, String name);
    List<Folder> findByUserIdAndParentIdAndDeletedFalse(Long userId, Long parentId);
    List<Folder> findByUserIdAndParentIdAndDeletedFalse(Long userId, Long parentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.userId = :userId AND f.deleted = false")
    Optional<Folder> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE folders SET deleted = TRUE, deleted_at = now()
        WHERE path <@ CAST(:parentPath AS ltree) AND deleted = FALSE
        """, nativeQuery = true)
    int softDeleteDescendants(@Param("parentPath") String parentPath);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE folders
        SET path = CASE
            WHEN path = CAST(:oldPrefix AS ltree)
            THEN CAST(:newPrefix AS ltree)
            ELSE CAST(:newPrefix || '.' || subpath(path, nlevel(CAST(:oldPrefix AS ltree))) AS ltree)
        END
        WHERE path <@ CAST(:oldPrefix AS ltree)
        """, nativeQuery = true)
    int moveDescendantPaths(@Param("oldPrefix") String oldPrefix,
                            @Param("newPrefix") String newPrefix);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        DELETE FROM folders WHERE deleted = TRUE AND deleted_at < :threshold
        """, nativeQuery = true)
    int hardDeleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
