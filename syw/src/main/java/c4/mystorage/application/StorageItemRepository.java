package c4.mystorage.application;

import c4.mystorage.domain.StorageItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StorageItemRepository extends CrudRepository<StorageItem, Long> {
    Optional<StorageItem> findByStoredNameAndDeletedAtIsNull(String storedName);

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
}
