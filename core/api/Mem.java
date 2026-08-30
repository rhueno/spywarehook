package noface.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Mem {

    private static final Map<String, byte[]> store = new LinkedHashMap<>();

    public static void put(String path, String text) {
        if (text == null) return;
        store.put(norm(path), text.getBytes(StandardCharsets.UTF_8));
    }

    public static void put(String path, byte[] data) {
        if (data == null || data.length == 0) return;
        store.put(norm(path), data);
    }

    public static byte[] zip() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (var e : store.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    public static void clear() {
        store.clear();
    }

    private static String norm(String p) {
        return p.replace('\\', '/');
    }

    private Mem() {}
}
