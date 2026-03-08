package com.mystorage.controller;

import com.mystorage.dto.response.FileResponse;
import com.mystorage.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalUploadController {

    private static final Pattern UPLOAD_URI_PATTERN =
        Pattern.compile("^/upload/(\\d+)\\?token=([a-f0-9\\-]+)$");

    private final FileService fileService;

    @PostMapping("/upload-callback")
    public ResponseEntity<FileResponse> uploadCallback(
            @RequestHeader("X-File-Path") String filePath,
            @RequestHeader("X-Original-URI") String originalUri,
            @RequestHeader("X-Upload-Secret") String uploadSecret) {

        // originalUri 파싱: /upload/{metadataId}?token=xxx
        URI uri = URI.create(originalUri);
        String path = uri.getPath();   // /upload/42
        String query = uri.getQuery(); // token=abc-def

        // metadataId 추출
        if (path == null || !path.startsWith("/upload/")) {
            return ResponseEntity.badRequest().build();
        }
        String idStr = path.substring("/upload/".length());
        // trailing slash 제거
        if (idStr.endsWith("/")) {
            idStr = idStr.substring(0, idStr.length() - 1);
        }

        Long metadataId;
        try {
            metadataId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        // token 추출
        String token = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring("token=".length());
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        FileResponse response = fileService.completeUpload(metadataId, token, filePath, uploadSecret);
        return ResponseEntity.ok(response);
    }
}
