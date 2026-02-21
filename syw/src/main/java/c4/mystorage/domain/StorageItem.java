package c4.mystorage.domain;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
public class StorageItem {
    @Id
    private Long id;
    private Long parentId;

    private Long ownerId;

    private String displayName;
    private String storedName;

    private Long size;  // in bytes
    private ItemType itemType;
    private String contentType;
    private String extraMetadata;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public StorageItem() {
    }

    public StorageItem(Long parentId,
                       Long ownerId,
                       String displayName,
                       String storedName,
                       Long size,
                       ItemType itemType,
                       String contentType,
                       String extraMetadata) {
        this.parentId = parentId;
        this.ownerId = ownerId;
        this.displayName = displayName;
        this.storedName = storedName;
        this.size = size;
        this.itemType = itemType;
        this.contentType = contentType;
        this.extraMetadata = extraMetadata;
    }

    public boolean isNotOwnedBy(Long ownerId) {
        return !this.ownerId.equals(ownerId);
    }

    public void rename(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
