package Services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UserService {
    private final File store;

    public UserService(File store) { this.store = store; }

    public UUID getOrCreateUserId() {
        try {
            if (store.exists()) {
                String s = new String(java.nio.file.Files.readAllBytes(store.toPath()), StandardCharsets.UTF_8).trim();
                return UUID.fromString(s);
            } else {
                UUID id = UUID.randomUUID();
                java.nio.file.Files.writeString(store.toPath(), id.toString(), StandardCharsets.UTF_8);
                return id;
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot access user store: " + e.getMessage());
        }
    }
}
