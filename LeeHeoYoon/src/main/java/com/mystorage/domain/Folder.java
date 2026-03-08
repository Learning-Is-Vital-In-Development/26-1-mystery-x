package com.mystorage.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "folders")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Setter
    @Column(nullable = false, length = 255)
    private String name;

    @Setter
    @Column(columnDefinition = "ltree")
    private String path;

    @Setter
    @Column(name = "parent_id")
    private Long parentId;

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
