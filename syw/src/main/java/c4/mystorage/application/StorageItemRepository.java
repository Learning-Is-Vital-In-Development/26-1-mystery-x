package c4.mystorage.application;

import c4.mystorage.domain.StorageItem;
import org.springframework.data.repository.CrudRepository;

public interface StorageItemRepository extends CrudRepository<StorageItem, Long> {
}
