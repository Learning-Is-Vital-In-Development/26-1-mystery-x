package c4.mystorage.presentation;

import c4.mystorage.application.StorageFileData;
import c4.mystorage.application.StorageItemCreate;
import c4.mystorage.application.StorageService;
import org.apache.tika.Tika;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
public class StorageController {
    private final Tika tika;
    private final StorageService storageService;

    public StorageController(Tika tika, StorageService storageService) {
        this.tika = tika;
        this.storageService = storageService;
    }

    @PostMapping(path = "/storage-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> save(
            @RequestHeader("X-OWNER-ID") Long ownerId,
            @RequestPart MultipartFile file,
            @RequestPart StorageItemPayload payload
    ) throws IOException {
        storageService.save(
                new StorageItemCreate(
                        payload.parentId(),
                        ownerId,
                        file.getOriginalFilename(),
                        file.getInputStream(),
                        file.getSize(),
                        payload.itemType(),
                        detectContentType(file),
                        payload.extraMetadata()
                )
        );
        return ResponseEntity.ok().build();
    }

    private String detectContentType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    @GetMapping("/storage-items/{storedName}")
    public ResponseEntity<Resource> download(
            @RequestHeader("X-OWNER-ID") Long ownerId,
            @PathVariable String storedName
    ) {
        StorageFileData fileData = storageService.getFile(ownerId, storedName);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileData.displayName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileData.contentType()))
                .contentLength(fileData.file().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(fileData.file()));
    }
}
