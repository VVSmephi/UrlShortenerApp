package Services;

import Interfaces.INotifier;
import Repositories.InMemoryRepository;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupService {
    private final InMemoryRepository repo;
    private final INotifier notifier;
    private ScheduledExecutorService es;

    public CleanupService(InMemoryRepository repo, INotifier notifier) {
        this.repo = repo;
        this.notifier = notifier;
    }

    public void start() {
        es = Executors.newSingleThreadScheduledExecutor();
        es.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            repo.findAll().forEach(l -> {
                if (now.isAfter(l.expiresAt())) {
                    repo.delete(l.id());
                    notifier.notify("Ссылка " + l.id() + " автоматически удалена: истёк срок действия");
                }
            });
        }, 1, 30, TimeUnit.MINUTES);
    }

    public void  stop() {
        if (es != null) es.shutdownNow();
    }
}
