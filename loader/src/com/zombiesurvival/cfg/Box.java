package com.zombiesurvival.cfg;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class Box {

    private static final int TAG = 128;

    static byte[] open(byte[] blob) throws Exception {
        byte[] key = load();
        ByteBuffer buf = ByteBuffer.wrap(blob);
        byte[] iv = new byte[12];
        buf.get(iv);
        byte[] enc = new byte[buf.remaining()];
        buf.get(enc);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG, iv));
        return c.doFinal(enc);
    }

    private static byte[] load() throws Exception {
        try (InputStream a = Box.class.getResourceAsStream("/assets/wsvc/wa.bin");
             InputStream b = Box.class.getResourceAsStream("/assets/wsvc/wb.bin")) {
            if (a == null || b == null) throw new IllegalStateException("key");
            byte[] p1 = a.readAllBytes();
            byte[] p2 = b.readAllBytes();
            byte[] out = new byte[p1.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) (p1[i] ^ p2[i]);
            }
            return out;
        }
    }

    private Box() {}
}
