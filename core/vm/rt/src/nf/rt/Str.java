package nf.rt;

import java.nio.charset.StandardCharsets;

public final class Str {

    private Str() {}

    public static String get(byte[] blob, byte[] key, int index) {
        if (blob == null || key == null || key.length == 0 || index < 0) return "";
        int pos = 0;
        int i = 0;
        while (i < index && pos + 1 < blob.length) {
            int len = (blob[pos] & 0xFF) | ((blob[pos + 1] & 0xFF) << 8);
            pos += 2 + len;
            i++;
        }
        if (pos + 1 >= blob.length) return "";
        int len = (blob[pos] & 0xFF) | ((blob[pos + 1] & 0xFF) << 8);
        pos += 2;
        byte[] dec = new byte[len];
        for (int j = 0; j < len && pos < blob.length; j++, pos++) {
            dec[j] = (byte) (blob[pos] ^ key[j % key.length] ^ (byte) (j * 131 + 17));
        }
        return new String(dec, StandardCharsets.UTF_8);
    }
}
