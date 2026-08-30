package nf.tr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.regex.Pattern;

final class Scan {

    private final Config cfg;
    private final List<Pattern> inc;
    private final List<Pattern> exc;

    Scan(Config cfg) {
        this.cfg = cfg;
        this.inc = cfg.includePatterns();
        this.exc = cfg.excludePatterns();
    }

    boolean protectClass(ClassNode cn) {
        if ((cn.access & Opcodes.ACC_RECORD) != 0) return false;
        return protectName(cn.name);
    }

    boolean protectName(String internal) {
        if (!internal.startsWith("noface/")) return false;
        if (internal.contains("$")) return false;
        if (internal.startsWith("nf/rt/")) return false;
        String dot = internal.replace('/', '.');
        for (Pattern p : exc) {
            if (p.matcher(dot).matches() || p.matcher(internal).matches()) return false;
        }
        if (inc.isEmpty()) return true;
        for (Pattern p : inc) {
            if (p.matcher(dot).matches() || p.matcher(internal).matches()) return true;
        }
        return false;
    }

    boolean innerClass(String internal) {
        return internal.contains("$");
    }

    boolean plainInner(ClassNode cn) {
        if (cn.superName != null && cn.superName.contains("/jna/")) return true;
        if (cn.interfaces != null) {
            for (String it : cn.interfaces) {
                if (it.contains("/jna/")) return true;
            }
        }
        return false;
    }

    boolean canStr(ClassNode cn) {
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) return false;
        String n = cn.name;
        if (n.startsWith("noface/")) return true;
        String p = cfg.pkg;
        return p != null && !p.isEmpty() && n.startsWith(p.endsWith("/") ? p : p + "/");
    }

    boolean passRemap(String internal) {
        if (!internal.startsWith("noface/")) return false;
        if (internal.equals("noface/browsers/Main")) return true;
        String dot = internal.replace('/', '.');
        for (Pattern p : exc) {
            if (p.matcher(dot).matches() || p.matcher(internal).matches()) return true;
        }
        return false;
    }

    boolean stubMethod(ClassNode cn, MethodNode mn) {
        if ((mn.access & Opcodes.ACC_STATIC) == 0) return false;
        if ((mn.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0) return false;
        if ("<init>".equals(mn.name)) return false;
        if ("<clinit>".equals(mn.name)) return false;
        if ((mn.access & Opcodes.ACC_BRIDGE) != 0) return false;
        if ((mn.access & Opcodes.ACC_SYNTHETIC) != 0) return false;
        return true;
    }

    boolean rename(String internal) {
        if (!cfg.rename) return false;
        if (!internal.startsWith("noface/")) return false;
        if (internal.equals("noface/browsers/Main")) return false;
        String dot = internal.replace('/', '.');
        for (Pattern p : exc) {
            if (p.matcher(dot).matches() || p.matcher(internal).matches()) return false;
        }
        return true;
    }
}
