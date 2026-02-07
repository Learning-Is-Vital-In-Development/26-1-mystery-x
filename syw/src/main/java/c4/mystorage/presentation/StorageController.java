package c4.mystorage.presentation;

import c4.mystorage.application.StorageItemCreate;
import c4.mystorage.application.StorageService;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

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
}
