package nf.rt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Frag {

    private static final Map<Long, byte[]> cache = new ConcurrentHashMap<>();

    private Frag() {}

    public static byte[] open(long id) {
        return cache.computeIfAbsent(id, Frag::load);
    }

    private static byte[] load(long id) {
        try {
            String idx = String.format("/META-INF/nf/i/%016x.bin", id);
            try (InputStream in = Frag.class.getResourceAsStream(idx)) {
                if (in == null) throw new IllegalStateException("idx");
                ByteBuffer buf = ByteBuffer.wrap(in.readAllBytes());
                int n = buf.getInt();
                int[] ord = new int[n];
                for (int i = 0; i < n; i++) ord[i] = buf.getInt();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int o : ord) {
                    String p = String.format("/META-INF/nf/c/%016x_%04d.bin", id, o);
                    try (InputStream ch = Frag.class.getResourceAsStream(p)) {
                        if (ch == null) throw new IllegalStateException("ch");
                        out.write(ch.readAllBytes());
                    }
                }
                return Cry.open(out.toByteArray(), Key.get());
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
