package Services;

import Enums.OpenResult;
import Models.ShortLink;
import Repositories.InMemoryRepository;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class LinkService {
    private final InMemoryRepository repo;
    private final Duration defaultTtl;

    public LinkService(InMemoryRepository repo, Duration defaultTtl) {
        this.repo = repo;
        this.defaultTtl = defaultTtl;
    }

    public ShortLink create(UUID owner, URI target, Integer limit, Duration ttlOverride) {
        Instant now = Instant.now();
        Duration ttl = ttlOverride != null ? ttlOverride : defaultTtl;
        String id = generateId(owner, target);
        // Гарантия уникальности
        while (repo.exists(id)) id = generateId(owner, target, UUID.randomUUID().toString());
        ShortLink link = new ShortLink(
                id, owner, target, now, now.plus(ttl),
                limit != null ? Math.max(0, limit) : 0,
                0, true
        );
        repo.save(link);
        return link;
    }

    public OpenResult open(String id) {
        ShortLink l = repo.get(id);
        if (l == null) return OpenResult.NOT_FOUND;
        Instant now = Instant.now();
        if (!l.active()) return OpenResult.INACTIVE;
        if (now.isAfter(l.expiresAt())) {
            repo.save(l.withActive(false));
            return OpenResult.EXPIRED;
        }
        if (l.maxClicks() > 0 && l.clicks() >= l.maxClicks()) {
            repo.save(l.withActive(false));
            return OpenResult.LIMIT_EXCEEDED;
        }

        int newClicks = l.clicks() + 1;
        repo.save(l.withClicks(newClicks));

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(l.target()); // открытие браузера
            } else {
                System.out.println("Desktop not supported; URL: " + l.target());
            }
        } catch (IOException e) {
            System.out.println("Cannot open browser: " + e.getMessage());
        }
        return OpenResult.OK;
    }

    private static String generateId(UUID owner, URI target) {
        return generateId(owner, target, owner.toString());
    }

    private static String generateId(UUID owner, URI target, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(owner.toString().getBytes(StandardCharsets.UTF_8));
            md.update((target.toString() + "|" + salt).getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            return base62(hash).substring(0, 8); // 8 символов
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final char[] B62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static String base62(byte[] bytes) {
        // простая упаковка: берём по 3 байта -> 4 символа base62 (упрощённый вариант)
        StringBuilder sb = new StringBuilder();
        long acc = 0; int bits = 0;
        for (byte b : bytes) {
            acc = (acc << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 6) {
                int idx = (int)((acc >> (bits - 6)) & 0x3F);
                sb.append(B62[idx % 62]);
                bits -= 6;
            }
        }
        if (bits > 0) {
            int idx = (int)((acc << (6 - bits)) & 0x3F);
            sb.append(B62[idx % 62]);
        }
        return sb.toString();
    }
}
