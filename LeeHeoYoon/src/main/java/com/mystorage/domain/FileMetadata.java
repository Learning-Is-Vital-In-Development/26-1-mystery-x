package com.mystorage.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_metadata")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "stored_name", nullable = false, unique = true)
    private UUID storedName;

    @Setter
    @Column(name = "folder_id")
    private Long folderId;

    @Setter
    @Column(name = "folder_path", columnDefinition = "ltree")
    private String folderPath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false)
    private UploadStatus uploadStatus;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Setter
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Setter
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
