package nf.rt;

import java.lang.management.ManagementFactory;

public final class Guard {

    private static volatile boolean ok = true;

    private Guard() {}

    public static void arm() {
        if (!ok) throw new SecurityException("g");
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String l = a.toLowerCase();
            if (l.contains("javaagent") || l.contains("agentlib") || l.contains("jdwp")
                    || l.startsWith("-xdebug") || l.startsWith("-xrunjdwp")
                    || l.contains("dynamicagent") || l.contains("suspend=y")) {
                ok = false;
                throw new SecurityException("g");
            }
        }
    }
}
