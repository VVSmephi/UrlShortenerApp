package Services;

import Models.ShortLink;
import Repositories.InMemoryRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class HttpRedirectServer {
    private final int port;
    private final InMemoryRepository repo;
    private HttpServer server;

    public HttpRedirectServer(int port, InMemoryRepository repo) {
        this.port = port; this.repo = repo;
    }
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/u", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("HTTP redirect on " + "http://localhost:" + port + "/u/<id>");
    }
    public void stop() { if (server != null) server.stop(0); }

    private void handle(HttpExchange ex) throws IOException {
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length != 3 || parts[2].isEmpty()) { send(ex, 400, "Bad short URL"); return; }
        String id = parts[2];
        ShortLink l = repo.get(id);
        if (l == null || !l.active()) { send(ex, 404, "Not found"); return; }
        if (Instant.now().isAfter(l.expiresAt())) { repo.delete(id); send(ex, 410, "Expired"); return; }
        if (l.maxClicks() > 0 && l.clicks() >= l.maxClicks()) { send(ex, 410, "Limit exceeded"); return; }

        repo.save(l.withClicks(l.clicks() + 1));
        ex.getResponseHeaders().add("Location", l.target().toString());
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }
    private static void send(HttpExchange ex, int code, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
}
