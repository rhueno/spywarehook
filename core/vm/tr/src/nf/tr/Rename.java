package nf.tr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class Rename {

    private final Config cfg;
    private final Map<String, String> map = new HashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    Rename(Config cfg) {
        this.cfg = cfg;
    }

    void register(String internal, boolean doRename) {
        if (!doRename || map.containsKey(internal)) return;
        map.put(internal, cfg.pkg + "/" + next());
    }

    void registerInner(String internal) {
        if (!cfg.rename || map.containsKey(internal)) return;
        int d = internal.lastIndexOf('$');
        if (d < 0) return;
        String outer = internal.substring(0, d);
        String suffix = internal.substring(d);
        String mappedOuter = map.get(outer);
        if (mappedOuter != null) {
            map.put(internal, mappedOuter + suffix);
        }
    }

    ClassNode apply(ClassNode cn) {
        ClassNode out = new ClassNode();
        cn.accept(new ClassRemapper(out, remapper()));
        return out;
    }

    String map(String internal) {
        return map.getOrDefault(internal, internal);
    }

    long id(String stubInternal) {
        return Id.hash(stubInternal);
    }

    String hiddenName(String stubInternal) {
        return stubInternal + "$H";
    }

    private Remapper remapper() {
        return new SimpleRemapper(map);
    }

    private String next() {
        String alpha = "oO0";
        int n = seq.getAndIncrement();
        StringBuilder sb = new StringBuilder();
        int min = 6;
        do {
            sb.append(alpha.charAt(n % alpha.length()));
            n /= alpha.length();
            min--;
        } while (n > 0 || min > 0);
        return sb.reverse().toString();
    }
}
