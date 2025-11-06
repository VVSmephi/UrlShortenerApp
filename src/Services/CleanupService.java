package Services;

import Repositories.InMemoryRepository;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupService {
    private final InMemoryRepository repo;
    private ScheduledExecutorService es;

    public CleanupService(InMemoryRepository repo) { this.repo = repo; }

    public void start() {
        es = Executors.newSingleThreadScheduledExecutor();
        es.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            repo.findAll().forEach(l -> {
                if (now.isAfter(l.expiresAt())) repo.delete(l.id());
            });
        }, 1, 30, TimeUnit.MINUTES); // каждые 30 мин
    }

    public void  stop() {
        if (es != null) es.shutdownNow();
    }
}
