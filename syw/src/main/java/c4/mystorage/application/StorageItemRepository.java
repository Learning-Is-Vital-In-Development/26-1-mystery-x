package c4.mystorage.application;

import c4.mystorage.domain.StorageItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StorageItemRepository extends CrudRepository<StorageItem, Long> {
    @Query("SELECT * FROM storage_item WHERE stored_name = :storedName AND deleted_at IS NULL LIMIT 1")
    Optional<StorageItem> findByStoredNameAndDeletedAtIsNull(@Param("storedName") String storedName);

    @Query("SELECT COUNT(*) FROM storage_item WHERE stored_name = :storedName AND deleted_at IS NULL")
    long countByStoredNameAndDeletedAtIsNull(@Param("storedName") String storedName);

    @Query("SELECT DISTINCT s1.stored_name FROM storage_item s1 " +
           "WHERE s1.stored_name IS NOT NULL " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM storage_item s2 " +
           "  WHERE s2.stored_name = s1.stored_name AND s2.deleted_at IS NULL" +
           ")")
    List<String> findOrphanStoredNames();

    @Query("SELECT * FROM storage_item WHERE id = :id AND item_type = :itemType AND deleted_at IS NULL")
    Optional<StorageItem> findByIdAndItemTypeAndDeletedAtIsNull(
            @Param("id") Long id,
            @Param("itemType") String itemType);

    @Query("SELECT COUNT(*) > 0 FROM storage_item " +
           "WHERE owner_id = :ownerId " +
           "AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId) " +
           "AND display_name = :displayName " +
           "AND item_type = :itemType " +
           "AND deleted_at IS NULL")
    boolean existsByOwnerAndParentAndNameAndType(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId,
            @Param("displayName") String displayName,
            @Param("itemType") String itemType);

    @Query("SELECT * FROM storage_item " +
           "WHERE owner_id = :ownerId AND parent_id = :parentId AND deleted_at IS NULL")
    List<StorageItem> findByOwnerIdAndParentIdAndDeletedAtIsNull(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId);

    @Query("SELECT * FROM storage_item " +
           "WHERE owner_id = :ownerId AND parent_id IS NULL AND deleted_at IS NULL")
    List<StorageItem> findByOwnerIdAndParentIdIsNullAndDeletedAtIsNull(
            @Param("ownerId") Long ownerId);

    @Query("SELECT * FROM storage_item WHERE parent_id = :parentId AND deleted_at IS NULL")
    List<StorageItem> findByParentIdAndDeletedAtIsNull(@Param("parentId") Long parentId);

    @Query("SELECT * FROM storage_item WHERE id = :id AND deleted_at IS NULL")
    Optional<StorageItem> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT parent_id FROM storage_item WHERE id = :id AND deleted_at IS NULL")
    Optional<Long> findParentIdById(@Param("id") Long id);

    @Query("SELECT COUNT(*) > 0 FROM storage_item " +
           "WHERE owner_id = :ownerId " +
           "AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId) " +
           "AND display_name = :displayName " +
           "AND deleted_at IS NULL")
    boolean existsByOwnerAndParentAndName(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId,
            @Param("displayName") String displayName);
}
