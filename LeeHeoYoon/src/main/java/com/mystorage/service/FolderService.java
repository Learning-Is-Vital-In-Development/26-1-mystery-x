package com.mystorage.service;

import com.mystorage.domain.FileMetadata;
import com.mystorage.domain.Folder;
import com.mystorage.domain.UploadStatus;
import com.mystorage.dto.response.FileResponse;
import com.mystorage.dto.response.FolderContentsResponse;
import com.mystorage.dto.response.FolderResponse;
import com.mystorage.exception.DuplicateNameException;
import com.mystorage.exception.FolderNotFoundException;
import com.mystorage.repository.FileMetadataRepository;
import com.mystorage.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository folderRepo;
    private final FileMetadataRepository fileRepo;

    @Transactional
    public FolderResponse createFolder(Long userId, String name, Long parentId) {
        validateFolderName(name);
        if (folderRepo.existsByUserIdAndParentIdAndNameAndDeletedFalse(userId, parentId, name)) {
            throw new DuplicateNameException("Folder '" + name + "' already exists here");
        }

        if (parentId != null) {
            getOwnedFolder(userId, parentId);
        }

        Folder folder = Folder.builder()
            .userId(userId)
            .name(name)
            .parentId(parentId)
            .build();
        // saveAndFlush로 ID 즉시 확보 + path 설정을 단일 트랜잭션 내에서 완료
        folder = folderRepo.saveAndFlush(folder);

        String path;
        if (parentId == null) {
            path = "u" + userId + ".f" + folder.getId();
        } else {
            Folder parent = folderRepo.getReferenceById(parentId);
            path = parent.getPath() + ".f" + folder.getId();
        }
        folder.setPath(path);
        // 두 번째 save는 같은 트랜잭션 내에서 path를 즉시 업데이트
        folderRepo.saveAndFlush(folder);

        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse renameFolder(Long userId, Long folderId, String newName) {
        validateFolderName(newName);
        Folder folder = getOwnedFolder(userId, folderId);

        if (folderRepo.existsByUserIdAndParentIdAndNameAndDeletedFalse(
                userId, folder.getParentId(), newName)) {
            throw new DuplicateNameException("Folder '" + newName + "' already exists here");
        }

        folder.setName(newName);
        folderRepo.save(folder);
        return FolderResponse.from(folder);
    }

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 1000;

    @Transactional(readOnly = true)
    public FolderContentsResponse listContents(Long userId, Long folderId, int page, int size) {
        // ★ 페이지네이션: 기본 200, 최대 1000 ★
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("name").ascending());

        String folderName;
        if (folderId != null) {
            Folder folder = getOwnedFolder(userId, folderId);
            folderName = folder.getName();
        } else {
            folderName = "root";
        }

        List<Folder> folders = folderRepo.findByUserIdAndParentIdAndDeletedFalse(userId, folderId, pageable);
        List<FileMetadata> files = fileRepo.findByUserIdAndFolderIdAndDeletedFalseAndUploadStatus(
            userId, folderId, UploadStatus.COMPLETED, pageable);

        return new FolderContentsResponse(
            folderId, folderName,
            folders.stream().map(FolderResponse::from).toList(),
            files.stream().map(FileResponse::from).toList()
        );
    }

    @Transactional
    public FolderResponse moveFolder(Long userId, Long folderId, Long targetFolderId) {
        // ★ Deadlock 방지: 항상 ID가 작은 폴더부터 lock 획득 ★
        Folder folder;
        Folder target = null;

        if (targetFolderId != null) {
            // ID 순서대로 pessimistic lock 획득
            long firstId = Math.min(folderId, targetFolderId);
            long secondId = Math.max(folderId, targetFolderId);

            Folder first = folderRepo.findByIdAndUserIdForUpdate(firstId, userId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + firstId));
            Folder second = folderRepo.findByIdAndUserIdForUpdate(secondId, userId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + secondId));

            folder = (folderId == firstId) ? first : second;
            target = (targetFolderId == firstId) ? first : second;
        } else {
            folder = folderRepo.findByIdAndUserIdForUpdate(folderId, userId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + folderId));
        }

        // ★ 이동 대상 위치에 동일 이름 폴더 존재 여부 체크 ★
        if (folderRepo.existsByUserIdAndParentIdAndNameAndDeletedFalse(
                userId, targetFolderId, folder.getName())) {
            throw new DuplicateNameException(
                "Folder '" + folder.getName() + "' already exists in target location");
        }

        String oldPath = folder.getPath();

        String newPath;
        if (targetFolderId == null) {
            newPath = "u" + userId + ".f" + folderId;
            folder.setParentId(null);
        } else {
            if (target.getPath().equals(oldPath) ||
                target.getPath().startsWith(oldPath + ".")) {
                throw new IllegalArgumentException("Cannot move folder into its own descendant");
            }

            newPath = target.getPath() + ".f" + folderId;
            folder.setParentId(targetFolderId);
        }

        folderRepo.moveDescendantPaths(oldPath, newPath);
        fileRepo.moveFilesUnderPath(oldPath, newPath);

        return FolderResponse.from(folderRepo.findById(folderId).orElseThrow());
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = getOwnedFolder(userId, folderId);

        folderRepo.softDeleteDescendants(folder.getPath());
        fileRepo.softDeleteFilesUnderPath(folder.getPath());
    }

    private void validateFolderName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        if (name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid folder name: " + name);
        }
        if (name.contains("/") || name.contains("\\") || name.contains("\0")) {
            throw new IllegalArgumentException("Folder name contains invalid characters");
        }
    }

    private Folder getOwnedFolder(Long userId, Long folderId) {
        return folderRepo.findByIdAndUserIdAndDeletedFalse(folderId, userId)
            .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + folderId));
    }
}
