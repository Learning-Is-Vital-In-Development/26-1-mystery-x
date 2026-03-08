package com.mystorage.controller;

import com.mystorage.dto.request.FolderCreateRequest;
import com.mystorage.dto.request.FolderRenameRequest;
import com.mystorage.dto.request.MoveRequest;
import com.mystorage.dto.response.FolderContentsResponse;
import com.mystorage.dto.response.FolderResponse;
import com.mystorage.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FolderCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(folderService.createFolder(userId, request.name(), request.parentId()));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> rename(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long folderId,
            @Valid @RequestBody FolderRenameRequest request) {

        return ResponseEntity.ok(folderService.renameFolder(userId, folderId, request.name()));
    }

    @GetMapping("/{folderId}/contents")
    public ResponseEntity<FolderContentsResponse> listContents(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        return ResponseEntity.ok(folderService.listContents(userId, folderId, page, size));
    }

    @GetMapping("/root/contents")
    public ResponseEntity<FolderContentsResponse> listRootContents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        return ResponseEntity.ok(folderService.listContents(userId, null, page, size));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long folderId) {

        folderService.deleteFolder(userId, folderId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{folderId}/move")
    public ResponseEntity<FolderResponse> move(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long folderId,
            @Valid @RequestBody MoveRequest request) {

        return ResponseEntity.ok(folderService.moveFolder(userId, folderId, request.targetFolderId()));
    }
}
