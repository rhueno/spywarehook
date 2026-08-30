package nf.tr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class Id {

    private Id() {}

    static long hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (d[i] & 0xFF);
            }
            return v;
        } catch (Exception e) {
            return s.hashCode();
        }
    }
}
