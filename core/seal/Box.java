package nf.seal;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

public final class Box {

    private static final int TAG = 128;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: Box in.jar out.dat keyA keyB");
            System.exit(1);
        }
        byte[] plain = Files.readAllBytes(Path.of(args[0]));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        byte[] keyB = new byte[32];
        new SecureRandom().nextBytes(keyB);
        byte[] keyA = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyA[i] = (byte) (key[i] ^ keyB[i]);
        }
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG, iv));
        byte[] enc = c.doFinal(plain);
        ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length);
        buf.put(iv);
        buf.put(enc);
        Files.write(Path.of(args[1]), buf.array());
        Files.write(Path.of(args[2]), keyA);
        Files.write(Path.of(args[3]), keyB);
    }

    private Box() {}
}
