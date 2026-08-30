package nf.tr;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

final class Seal {

    private static final int TAG = 128;

    private Seal() {}

    static byte[] pack(byte[] plain, byte[] key) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG, iv));
            byte[] enc = c.doFinal(plain);
            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length);
            buf.put(iv);
            buf.put(enc);
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
