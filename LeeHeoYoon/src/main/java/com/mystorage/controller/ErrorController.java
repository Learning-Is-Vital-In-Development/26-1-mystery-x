package com.mystorage.controller;

import com.mystorage.dto.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/error")
public class ErrorController {

    @GetMapping("/file-too-large")
    public ResponseEntity<ErrorResponse> fileTooLarge() {
        return ResponseEntity.status(413)
                .body(new ErrorResponse(413, "PAYLOAD_TOO_LARGE", "File exceeds maximum upload size (300MB)"));
    }
}
