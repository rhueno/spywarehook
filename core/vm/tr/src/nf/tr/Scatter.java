package nf.tr;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

final class Scatter {

    private static final int CHUNK = 1024;
    private static final double DECOY = 0.45;

    private Scatter() {}

    static Map<String, byte[]> pack(long id, byte[] sealed) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        SecureRandom rnd = new SecureRandom();
        int chunks = (sealed.length + CHUNK - 1) / CHUNK;
        int decoys = Math.max(1, (int) Math.ceil(chunks * DECOY));
        int slots = chunks + decoys;
        List<Integer> pick = new ArrayList<>();
        for (int i = 0; i < slots; i++) pick.add(i);
        Collections.shuffle(pick, rnd);
        int[] ord = new int[chunks];
        for (int i = 0; i < chunks; i++) ord[i] = pick.get(i);

        ByteBuffer idx = ByteBuffer.allocate(4 + chunks * 4);
        idx.putInt(chunks);
        for (int o : ord) idx.putInt(o);
        out.put(String.format("META-INF/nf/i/%016x.bin", id), idx.array());

        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < chunks; i++) {
            used.add(ord[i]);
            int a = i * CHUNK;
            int b = Math.min(a + CHUNK, sealed.length);
            out.put(String.format("META-INF/nf/c/%016x_%04d.bin", id, ord[i]),
                    Arrays.copyOfRange(sealed, a, b));
        }
        for (int s = 0; s < slots; s++) {
            if (used.contains(s)) continue;
            byte[] junk = new byte[CHUNK];
            rnd.nextBytes(junk);
            out.put(String.format("META-INF/nf/c/%016x_%04d.bin", id, s), junk);
        }
        return out;
    }
}
