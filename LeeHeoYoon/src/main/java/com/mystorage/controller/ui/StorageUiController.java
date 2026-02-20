package com.mystorage.controller.ui;

import com.mystorage.domain.FileMetadata;
import com.mystorage.exception.StorageFileNotFoundException;
import com.mystorage.service.FileService;
import com.mystorage.storage.PhysicalStorageManager;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class StorageUiController {

    private final FileService fileService;
    private final PhysicalStorageManager storageManager;

    @GetMapping
    public String index() {
        return "index";
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("userId") Long userId,
            @PathVariable Long fileId) throws IOException {
        FileMetadata file = fileService.getFileForDownload(userId, fileId);
        Path filePath = storageManager.getStoredFilePath(file.getStoredName());

        if (!Files.exists(filePath)) {
            throw new StorageFileNotFoundException("Physical file not found");
        }

        InputStream inputStream = Files.newInputStream(filePath);
        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String asciiOnly = file.getOriginalName().replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
            .header("Content-Disposition",
                "attachment; filename=\"" + asciiOnly + "\"; filename*=UTF-8''" + encoded)
            .header("Content-Type",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            .contentLength(file.getFileSize())
            .body(new InputStreamResource(inputStream));
    }
}
