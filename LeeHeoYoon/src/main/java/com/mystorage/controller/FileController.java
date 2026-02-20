package com.mystorage.controller;

import com.mystorage.domain.FileMetadata;
import com.mystorage.dto.request.CopyRequest;
import com.mystorage.dto.request.MoveRequest;
import com.mystorage.dto.response.FileResponse;
import com.mystorage.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping
    public ResponseEntity<FileResponse> upload(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId) {

        FileResponse response = fileService.upload(userId, file, folderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Void> download(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long fileId) {

        FileMetadata file = fileService.getFileForDownload(userId, fileId);
        return ResponseEntity.ok()
            .header("X-Accel-Redirect", "/storage-internal/" + file.getStoredName())
            .header("Content-Disposition", buildContentDisposition(file.getOriginalName()))
            .header("Content-Type",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            // ★ v4 Consensus: Content-Length 제거 — Nginx가 파일에서 직접 설정 ★
            .build();
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long fileId) {

        fileService.softDelete(userId, fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<FileResponse> getMetadata(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long fileId) {

        return ResponseEntity.ok(fileService.getMetadata(userId, fileId));
    }

    @PatchMapping("/{fileId}/move")
    public ResponseEntity<FileResponse> move(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long fileId,
            @Valid @RequestBody MoveRequest request) {

        return ResponseEntity.ok(fileService.moveFile(userId, fileId, request.targetFolderId()));
    }

    @PostMapping("/{fileId}/copy")
    public ResponseEntity<FileResponse> copy(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long fileId,
            @Valid @RequestBody CopyRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fileService.copyFile(userId, fileId, request.targetFolderId()));
    }

    /**
     * RFC 6266 + RFC 8187 준수 Content-Disposition 헤더 값 생성.
     * filename: ASCII-only 폴백 (구형 브라우저용)
     * filename*: UTF-8 percent-encoded (최신 브라우저용)
     */
    private String buildContentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
            .replace("+", "%20");
        String asciiOnly = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "attachment; filename=\"" + asciiOnly + "\"; filename*=UTF-8''" + encoded;
    }
}
