package noface.browsers;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public final class Aes {

    private static final int TAG_BITS = 128;
    private static final int NONCE = 12;
    private static final int MIN_LEN = 3 + NONCE + 16;

    private static final ThreadLocal<Cipher> CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance("AES/GCM/NoPadding");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    });

    public static String decrypt(byte[] enc, byte[] masterKey, byte[] abeKey) {
        byte[] bytes = decryptBytes(enc, masterKey, abeKey);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static String decryptCookie(byte[] enc, byte[] masterKey, byte[] abeKey, String host) {
        if (enc == null || enc.length < 4) return null;
        boolean v20 = enc.length >= 3 && enc[0] == 'v' && enc[1] == '2' && enc[2] == '0';
        if (v20 && abeKey != null) {
            String s = decryptCookieV20(enc, abeKey, host);
            if (s != null && !s.isEmpty()) return s;
        }
        byte[] plain = decryptBytes(enc, masterKey, abeKey);
        if (plain == null) return null;
        byte[] fin = finishCookie(plain, host, v20);
        if (fin == null || fin.length == 0) return null;
        return new String(fin, StandardCharsets.UTF_8);
    }

    public static String decryptCookieV20(byte[] blob, byte[] abeKey, String domain) {
        if (abeKey == null || blob == null || blob.length < MIN_LEN) return null;
        byte[] plain = gcmBytes(blob, abeKey, 3);
        if (plain == null || plain.length == 0) return null;
        byte[] fin = finishCookie(plain, domain, true);
        if (fin == null || fin.length == 0) return null;
        return new String(fin, StandardCharsets.UTF_8);
    }

    private static byte[] finishCookie(byte[] decrypted, String hostKey, boolean v20) {
        if (decrypted == null || decrypted.length == 0) return decrypted;
        if (decrypted.length <= 32) return decrypted;
        if (hostKey != null && !hostKey.isEmpty()) {
            for (String host : hostVariants(hostKey)) {
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] expected = md.digest(host.getBytes(StandardCharsets.UTF_8));
                    if (Arrays.equals(expected, Arrays.copyOf(decrypted, 32))) {
                        return Arrays.copyOfRange(decrypted, 32, decrypted.length);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (v20) return Arrays.copyOfRange(decrypted, 32, decrypted.length);
        if (binaryPrefix(decrypted)) return Arrays.copyOfRange(decrypted, 32, decrypted.length);
        return decrypted;
    }

    private static String[] hostVariants(String hostKey) {
        if (hostKey == null || hostKey.isEmpty()) return new String[0];
        if (hostKey.startsWith(".")) {
            return new String[]{hostKey, hostKey.substring(1)};
        }
        return new String[]{hostKey, "." + hostKey};
    }

    private static boolean binaryPrefix(byte[] data) {
        for (int i = 0; i < 32 && i < data.length; i++) {
            int b = data[i] & 0xFF;
            if (b < 32 && b != 9 && b != 10 && b != 13) return true;
        }
        return false;
    }

    public static byte[] decryptBytes(byte[] enc, byte[] masterKey, byte[] abeKey) {
        if (enc == null || enc.length < 4) return null;
        if (isLegacyDpapi(enc)) return Dpapi.unprotect(enc);
        byte[] blob = prepare(enc);
        if (blob == null || blob.length < MIN_LEN) return null;
        String ver = new String(blob, 0, 3, StandardCharsets.US_ASCII);
        return switch (ver) {
            case "v10", "v11" -> gcmBytes(blob, masterKey, 3);
            case "v20" -> abeKey != null ? gcmBytes(blob, abeKey, 3) : null;
            default -> null;
        };
    }

    private static byte[] prepare(byte[] enc) {
        if (enc.length >= 3) {
            String head = new String(enc, 0, 3, StandardCharsets.US_ASCII);
            if (head.equals("v10") || head.equals("v11") || head.equals("v20")) return enc;
        }
        for (int i = 1; i < Math.min(enc.length - MIN_LEN, 64); i++) {
            if (enc[i] == 'v' && i + 2 < enc.length) {
                if (enc[i + 1] == '1' && (enc[i + 2] == '0' || enc[i + 2] == '1'))
                    return Arrays.copyOfRange(enc, i, enc.length);
                if (enc[i + 1] == '2' && enc[i + 2] == '0')
                    return Arrays.copyOfRange(enc, i, enc.length);
            }
        }
        return enc;
    }

    private static boolean isLegacyDpapi(byte[] blob) {
        return blob[0] == 0x01 && blob[1] == 0x00 && blob[2] == 0x00 && blob[3] == 0x00;
    }

    private static byte[] gcmBytes(byte[] enc, byte[] key, int header) {
        if (key == null || key.length < 16) return null;
        if (enc.length < header + NONCE + 16) return null;
        try {
            byte[] nonce = Arrays.copyOfRange(enc, header, header + NONCE);
            byte[] ct = Arrays.copyOfRange(enc, header + NONCE, enc.length);
            byte[] k = key.length == 32 ? key : (key.length > 32 ? Arrays.copyOf(key, 32) : key);
            SecretKeySpec spec = new SecretKeySpec(k, "AES");
            GCMParameterSpec gcm = new GCMParameterSpec(TAG_BITS, nonce);
            Cipher cipher = CIPHER.get();
            cipher.init(Cipher.DECRYPT_MODE, spec, gcm);
            return cipher.doFinal(ct);
        } catch (Exception e) {
            return null;
        }
    }

    private Aes() {}
}
