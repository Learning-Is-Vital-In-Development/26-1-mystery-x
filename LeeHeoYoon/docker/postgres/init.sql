CREATE EXTENSION IF NOT EXISTS ltree;

-- FOLDERS TABLE
CREATE TABLE folders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    path        LTREE,
    parent_id   BIGINT       REFERENCES folders(id),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP
);

CREATE UNIQUE INDEX uq_folder_user_parent_name
    ON folders (user_id, COALESCE(parent_id, -1), name)
    WHERE deleted = FALSE;

CREATE INDEX idx_folders_path_gist ON folders USING GIST (path);
CREATE INDEX idx_folders_user_parent ON folders (user_id, parent_id) WHERE deleted = FALSE;
CREATE INDEX idx_folders_deleted ON folders (deleted, deleted_at) WHERE deleted = TRUE;

-- FILE METADATA TABLE
CREATE TABLE file_metadata (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    original_name   VARCHAR(500) NOT NULL,
    stored_name     UUID         NOT NULL UNIQUE,
    folder_id       BIGINT       REFERENCES folders(id),
    folder_path     LTREE,
    file_size       BIGINT       NOT NULL,
    content_type    VARCHAR(255),
    upload_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP
);

CREATE UNIQUE INDEX uq_file_user_folder_name
    ON file_metadata (user_id, COALESCE(folder_id, -1), original_name)
    WHERE deleted = FALSE;

CREATE INDEX idx_files_user_folder ON file_metadata (user_id, folder_id) WHERE deleted = FALSE;
CREATE INDEX idx_files_folder_path_gist ON file_metadata USING GIST (folder_path);
CREATE INDEX idx_files_deleted ON file_metadata (deleted, deleted_at) WHERE deleted = TRUE;
CREATE INDEX idx_files_upload_status ON file_metadata (upload_status, created_at)
    WHERE upload_status IN ('PENDING', 'FAILED');
