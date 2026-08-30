package nf.rt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Launch {

    public static void main(String[] args) {
        go(args);
    }

    public static void go(String[] args) {
        try {
            Guard.arm();
            long id = readId();
            Gate.warm(id);
            Gate.call(id, "go", "([Ljava/lang/String;)V", true, new Object[]{args});
        } catch (Throwable t) {
            if (Boolean.getBoolean("nf.d")) t.printStackTrace();
        }
    }

    private static long readId() throws Exception {
        try (InputStream in = Launch.class.getResourceAsStream("/META-INF/nf/e.txt")) {
            if (in == null) throw new IllegalStateException("e");
            String hex = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return Long.parseUnsignedLong(hex, 16);
        }
    }

    private Launch() {}
}
