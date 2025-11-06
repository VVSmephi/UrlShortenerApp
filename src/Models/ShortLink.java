package Models;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record ShortLink(
        String id,
        UUID owner,
        URI target,
        Instant createdAt,
        Instant expiresAt,
        int maxClicks,
        int clicks,
        boolean active
) {
    public ShortLink withClicks(int v) { return new ShortLink(id, owner, target, createdAt, expiresAt, maxClicks, v, active); }
    public ShortLink withActive(boolean a) { return new ShortLink(id, owner, target, createdAt, expiresAt, maxClicks, clicks, a); }
    public ShortLink withMaxClicks(int m) { return new ShortLink(id, owner, target, createdAt, expiresAt, m, clicks, active); }
    public ShortLink withExpiresAt(Instant e) { return new ShortLink(id, owner, target, createdAt, e, maxClicks, clicks, active); }
}
