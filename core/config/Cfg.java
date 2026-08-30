package noface.config;

public final class Cfg {

    public static final String USER = "8691477007";
    public static final String KEY = "NF-XIY7-46NT-T2J6";
    public static final String OWN = "keshxrdd";
    public static final boolean DEBUG = false;
    public static final boolean ANTI_VM = false;

    public static String panelLabel() {
        return S.e("SpywareHook");
    }

    public static String panelUrl() {
        return S.e("https://spywarehook.com");
    }

    public static String zipName() {
        return S.e("spywarehook.zip");
    }

    public static String tgLink() {
        return S.e("https://t.me/spywarehookbot");
    }

    public static String footer() {
        String w = who();
        return w.isEmpty() ? KEY : w + " (" + KEY + ")";
    }

    public static String userFoot() {
        String w = who();
        return w.isEmpty() ? S.e("t.me/spywarehook") : w;
    }

    private static String who() {
        String u = tag(USER);
        String o = tag(OWN);
        if (o.isEmpty()) return u;
        if (u.isEmpty()) return o;
        return o + " · " + u;
    }

    private static String tag(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        if (t.startsWith("@") || t.startsWith("t.me/")) return t;
        boolean digits = true;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                digits = false;
                break;
            }
        }
        return digits ? t : "@" + t;
    }

    public static boolean lab() {
        return KEY.equals(S.e("NF-61O3-6OLZ-F8J2"));
    }

    private Cfg() {}
}
