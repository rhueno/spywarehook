package noface.browsers.win;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface Crypt32Lib extends StdCallLibrary {

    boolean CryptUnprotectData(
            WinCrypt.DATA_BLOB pDataIn,
            PointerByReference ppszDataDescr,
            WinCrypt.DATA_BLOB pOptionalEntropy,
            Pointer pvReserved,
            Pointer pPromptStruct,
            int dwFlags,
            WinCrypt.DATA_BLOB pDataOut
    );

    static Crypt32Lib load() {
        try {
            return Native.load("crypt32", Crypt32Lib.class, W32APIOptions.DEFAULT_OPTIONS);
        } catch (Throwable t) {
            return null;
        }
    }
}
