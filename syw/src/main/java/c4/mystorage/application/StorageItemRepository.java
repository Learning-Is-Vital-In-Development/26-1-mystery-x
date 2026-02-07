package c4.mystorage.application;

import c4.mystorage.domain.StorageItem;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface StorageItemRepository extends CrudRepository<StorageItem, Long> {
    Optional<StorageItem> findByStoredName(String storedName);
}
