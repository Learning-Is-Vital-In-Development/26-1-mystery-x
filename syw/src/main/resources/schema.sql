CREATE TABLE IF NOT EXISTS storage_item
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id      BIGINT,
    owner_id       BIGINT,
    display_name   VARCHAR(255),
    stored_name    VARCHAR(36),
    size           BIGINT,
    item_type      VARCHAR(50),
    content_type   VARCHAR(255),
    extra_metadata TEXT,
    created_at     DATETIME,
    updated_at     DATETIME,
    deleted_at     DATETIME,
    INDEX idx_stored_name(stored_name),
    INDEX idx_owner_parent_name_type (owner_id, parent_id, display_name, item_type)
);
