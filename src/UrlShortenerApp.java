import Enums.OpenResult;
import Interfaces.INotifier;
import Models.ShortLink;
import Repositories.InMemoryRepository;
import Services.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Pattern;

public class UrlShortenerApp {

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        try (InputStream in = new FileInputStream("application.properties")) {
            config.load(in);
        }

        long ttlHours = Long.parseLong(config.getProperty("shortener.ttl.hours"));
        int maxClicks = Integer.parseInt(config.getProperty("shortener.maxClicks"));
        String domainBase = config.getProperty("shortener.domain");
        int cleanupMinutes = Integer.parseInt(config.getProperty("shortener.cleanup.period.minutes"));

        UserService userService = new UserService(new File(System.getProperty("user.home"), ".shortener.uuid"));
        UUID userId = userService.getOrCreateUserId();

        INotifier notifier = new ConsoleNotifier();
        InMemoryRepository repo = new InMemoryRepository();
        LinkService linkService = new LinkService(repo, notifier, Duration.ofHours(ttlHours));
        CleanupService cleanup = new CleanupService(repo, notifier);
        cleanup.start();

        HttpRedirectServer http = new HttpRedirectServer(8080, repo);
        http.start();

        System.out.println("Your UUID: " + userId);
        System.out.println("Commands: create <url> [--limit N] [--ttl-hours H], open <id>, list, delete <id>, set-limit <id> <N>, set-ttl <id> <hours>, help, exit");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("exit")) break;

                try {
                    handle(line, userId, linkService, repo, domainBase, maxClicks, cleanupMinutes);
                } catch (Exception ex) {
                    System.out.println("Error: " + ex.getMessage());
                }
            }
        }

        cleanup.stop();

        http.stop();
    }

    private static void handle(String line, UUID userId, LinkService linkService, InMemoryRepository repo,
                               String domainBase, int maxClicks, int cleanupMinutes) throws Exception {
        String[] parts = tokenize(line);
        switch (parts[0].toLowerCase()) {
            case "help" -> System.out.println("""
                create <url> [--limit N] [--ttl-hours H]
                open <id>
                list
                delete <id>
                set-limit <id> <N>
                set-ttl <id> <hours>
                exit
                """);
            case "create" -> {
                if (parts.length < 2) throw new IllegalArgumentException("create <url> [--limit N] [--ttl-hours H]");
                URI target = validateUrl(parts[1]);
                Integer limit = null;
                Long customTtlHours = null;
                for (int i = 2; i < parts.length; i++) {
                    if ("--limit".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                        limit = Integer.parseInt(parts[++i]);
                    } else if ("--ttl-hours".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                        customTtlHours = Long.parseLong(parts[++i]);
                    }
                }

                int finalLimit = (limit != null) ? limit : maxClicks;
                long finalTtlHours = (customTtlHours != null) ? customTtlHours : cleanupMinutes;
                Duration ttl = Duration.ofHours(finalTtlHours);
                ShortLink link = linkService.create(userId, target, finalLimit, ttl);
                System.out.printf("%s%s | %s | clicks %d/%s | exp %s | active=%s%n",
                        domainBase, link.id(), link.target(), link.clicks(),
                        link.maxClicks()==0 ? "∞" : link.maxClicks(), link.expiresAt(), link.active());
            }
            case "open" -> {
                if (parts.length < 2) throw new IllegalArgumentException("open <id>");
                String id = parts[1];
                OpenResult r = linkService.open(id);
                switch (r) {
                    case OK -> System.out.println("Opened in browser");
                    case NOT_FOUND -> System.out.println("Link not found");
                    case EXPIRED -> System.out.println("Link expired — access blocked");
                    case LIMIT_EXCEEDED -> System.out.println("Click limit exceeded — access blocked");
                    case INACTIVE -> System.out.println("Link inactive");
                }
            }
            case "list" -> {
                repo.findByOwner(userId).forEach(l -> {
                    System.out.printf("%s%s | %s | clicks %d/%s | exp %s | active=%s%n",
                            domainBase, l.id(), l.target(), l.clicks(),
                            l.maxClicks()==0 ? "∞" : l.maxClicks(), l.expiresAt(), l.active());
                });
            }
            case "delete" -> {
                if (parts.length < 2) throw new IllegalArgumentException("delete <id>");
                ensureOwner(userId, parts[1], repo);
                repo.delete(parts[1]);
                System.out.println("Deleted");
            }
            case "set-limit" -> {
                if (parts.length < 3) throw new IllegalArgumentException("set-limit <id> <N>");
                ensureOwner(userId, parts[1], repo);
                ShortLink l = repo.get(parts[1]);
                l = l.withMaxClicks(Integer.parseInt(parts[2]));
                repo.save(l);
                System.out.println("Updated limit");
            }
            case "set-ttl" -> {
                if (parts.length < 3) throw new IllegalArgumentException("set-ttl <id> <hours>");
                ensureOwner(userId, parts[1], repo);
                ShortLink l = repo.get(parts[1]);
                Duration ttl = Duration.ofHours(Long.parseLong(parts[2]));
                l = l.withExpiresAt(l.createdAt().plus(ttl));
                repo.save(l);
                System.out.println("Updated TTL");
            }
            default -> System.out.println("Unknown command. Type 'help'.");
        }
    }

    private static void ensureOwner(UUID userId, String id, InMemoryRepository repo) {
        ShortLink l = repo.get(id);
        if (l == null) throw new IllegalArgumentException("Not found");
        if (!l.owner().equals(userId)) throw new SecurityException("Forbidden: not your link");
    }

    private static String[] tokenize(String line) {
        return Arrays.stream(line.split("\\s+")).toArray(String[]::new);
    }

    private static final Pattern URL_RX = Pattern.compile("^(https?://).+");

    private static URI validateUrl(String s) {
        if (!URL_RX.matcher(s).matches()) throw new IllegalArgumentException("URL must start with http(s)://");
        try { return new URI(s); } catch (URISyntaxException e) { throw new IllegalArgumentException("Bad URL"); }
    }
}
