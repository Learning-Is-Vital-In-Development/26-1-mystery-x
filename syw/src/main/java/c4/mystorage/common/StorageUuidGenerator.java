package c4.mystorage.common;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StorageUuidGenerator implements UuidGenerator {
    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
