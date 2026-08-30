package nf.rt;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Vault {

    private static final Map<Long, Class<?>> loaded = new ConcurrentHashMap<>();
    private static final Map<String, Method> methods = new ConcurrentHashMap<>();
    private static final Map<Long, String> anchors = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> loading = new ThreadLocal<>();

    private Vault() {}

    public static void warm(long id) {
        define(id);
    }

    static boolean has(long id) {
        return loaded.containsKey(id);
    }

    static boolean loading(long id) {
        Long cur = loading.get();
        return cur != null && cur == id;
    }

    public static Class<?> define(long id) {
        Class<?> c = loaded.get(id);
        if (c != null) return c;
        return loaded.computeIfAbsent(id, Vault::load);
    }

    public static void mapAnchor(long id, String stubDot) {
        anchors.put(id, stubDot);
    }

    private static Class<?> load(long id) {
        loading.set(id);
        try {
            byte[] raw = read(id);
            String stub = anchors.get(id);
            if (stub == null) {
                String namePath = String.format("/META-INF/nf/n/%016x.txt", id);
                try (InputStream nm = Vault.class.getResourceAsStream(namePath)) {
                    if (nm == null) throw new IllegalStateException("name " + Long.toHexString(id));
                    String internal = new String(nm.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    stub = internal.substring(0, internal.length() - 2).replace('/', '.');
                }
            }
            Class<?> anchor = Class.forName(stub);
            MethodHandles.Lookup lk = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup());
            return lk.defineClass(raw);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        } finally {
            loading.remove();
        }
    }

    private static byte[] read(long id) throws Exception {
        String idx = String.format("/META-INF/nf/i/%016x.bin", id);
        try (InputStream in = Vault.class.getResourceAsStream(idx)) {
            if (in != null) return Frag.open(id);
        }
        String path = String.format("/META-INF/nf/v/%016x.bin", id);
        try (InputStream in = Vault.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("blob " + Long.toHexString(id));
            return Cry.open(in.readAllBytes(), Key.get());
        }
    }

    static Method pick(Class<?> c, String name, String desc) throws NoSuchMethodException {
        String k = c.getName() + "#" + name + desc;
        Method m = methods.get(k);
        if (m != null) return m;
        ClassLoader cl = c.getClassLoader() == null ? ClassLoader.getSystemClassLoader() : c.getClassLoader();
        MethodType mt = MethodType.fromMethodDescriptorString(desc, cl);
        Class<?>[] params = new Class<?>[mt.parameterCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = mt.parameterType(i);
        }
        m = c.getDeclaredMethod(name, params);
        m.setAccessible(true);
        methods.put(k, m);
        return m;
    }
}
