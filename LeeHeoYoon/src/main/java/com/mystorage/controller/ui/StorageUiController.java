package com.mystorage.controller.ui;

import com.mystorage.domain.FileMetadata;
import com.mystorage.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class StorageUiController {

    private final FileService fileService;

    @GetMapping
    public String index() {
        return "index";
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Void> download(
            @RequestParam("userId") Long userId,
            @PathVariable Long fileId) {
        FileMetadata file = fileService.getFileForDownload(userId, fileId);

        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String asciiOnly = file.getOriginalName().replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
            .header("X-Accel-Redirect", "/storage-internal/" + file.getStoredName())
            .header("Content-Disposition",
                "attachment; filename=\"" + asciiOnly + "\"; filename*=UTF-8''" + encoded)
            .header("Content-Type",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            .build();
    }
}
