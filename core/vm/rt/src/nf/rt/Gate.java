package nf.rt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Gate {

    private static boolean armed;

    private Gate() {}

    public static void warm(long id) {
        if (!armed) {
            Guard.arm();
            armed = true;
        }
        if (Vault.has(id)) return;
        if (Vault.loading(id)) return;
        Vault.define(id);
    }

    public static Object call(long id, String name, String desc, boolean statik, Object[] args) throws Throwable {
        Class<?> c = Vault.define(id);
        Method m = Vault.pick(c, name, desc);
        try {
            if (statik) {
                return m.invoke(null, slice(args, 0));
            }
            if (args == null || args.length == 0) throw new IllegalStateException("inst");
            return m.invoke(args[0], slice(args, 1));
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            throw t == null ? e : t;
        }
    }

    private static Object[] slice(Object[] args, int off) {
        if (args == null || args.length <= off) return new Object[0];
        Object[] out = new Object[args.length - off];
        System.arraycopy(args, off, out, 0, out.length);
        return out;
    }

    public static boolean toBool(Object o) {
        return o instanceof Boolean b && b;
    }

    public static int toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
}
