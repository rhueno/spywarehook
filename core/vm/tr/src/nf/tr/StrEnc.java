package nf.tr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

final class StrEnc {

    private static final int MAX_BLOB = 65536;
    private static final int MAX_STR = 8192;

    private StrEnc() {}

    static void apply(ClassNode cn) {
        List<String> strings = collect(cn);
        if (strings.isEmpty()) return;

        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] blob = pack(strings, key);

        cn.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "_sb", "[B", null, null));
        cn.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "_sk", "[B", null, null));

        clinit(cn, blob, key);
        patch(cn, strings);
        wipeFields(cn, strings);
    }

    private static List<String> collect(ClassNode cn) {
        List<String> out = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                    if (!s.isEmpty() && !out.contains(s)) out.add(s);
                }
            }
        }
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                if (fn.value instanceof String s && !s.isEmpty() && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static byte[] pack(List<String> strings, byte[] key) {
        byte[] blob = new byte[MAX_BLOB];
        int pos = 0;
        for (String s : strings) {
            byte[] utf = s.getBytes(StandardCharsets.UTF_8);
            if (utf.length > MAX_STR || pos + 2 + utf.length > blob.length) break;
            blob[pos++] = (byte) (utf.length & 0xFF);
            blob[pos++] = (byte) ((utf.length >> 8) & 0xFF);
            for (int i = 0; i < utf.length; i++) {
                blob[pos++] = (byte) (utf[i] ^ key[i % key.length] ^ (byte) (i * 131 + 17));
            }
        }
        byte[] out = new byte[pos];
        System.arraycopy(blob, 0, out, 0, pos);
        return out;
    }

    private static void clinit(ClassNode cn, byte[] blob, byte[] key) {
        MethodNode cl = findCl(cn);
        InsnList il = new InsnList();
        il.add(arr(cn.name, blob, "_sb"));
        il.add(arr(cn.name, key, "_sk"));
        cl.instructions.insert(il);
    }

    private static InsnList arr(String owner, byte[] data, String field) {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(data.length));
        il.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));
        for (int i = 0; i < data.length; i++) {
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode(i));
            il.add(new LdcInsnNode((int) data[i]));
            il.add(new InsnNode(Opcodes.BASTORE));
        }
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field, "[B"));
        return il;
    }

    private static void patch(ClassNode cn, List<String> strings) {
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                    int i = strings.indexOf(s);
                    if (i < 0) continue;
                    InsnList rep = new InsnList();
                    rep.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "_sb", "[B"));
                    rep.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "_sk", "[B"));
                    rep.add(new LdcInsnNode(i));
                    rep.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "nf/rt/Str", "get",
                            "([B[BI)Ljava/lang/String;", false));
                    mn.instructions.insert(ldc, rep);
                    mn.instructions.remove(ldc);
                }
            }
        }
    }

    private static void wipeFields(ClassNode cn, List<String> strings) {
        if (cn.fields == null) return;
        MethodNode cl = findCl(cn);
        InsnList init = new InsnList();
        for (FieldNode fn : cn.fields) {
            if (!(fn.value instanceof String s)) continue;
            int i = strings.indexOf(s);
            if (i < 0) continue;
            fn.value = null;
            init.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "_sb", "[B"));
            init.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "_sk", "[B"));
            init.add(new LdcInsnNode(i));
            init.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "nf/rt/Str", "get",
                    "([B[BI)Ljava/lang/String;", false));
            init.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, fn.name, fn.desc));
        }
        if (init.size() > 0) {
            for (AbstractInsnNode insn : cl.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    cl.instructions.insertBefore(insn, init);
                    break;
                }
            }
        }
    }

    private static MethodNode findCl(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if ("<clinit>".equals(mn.name)) return mn;
        }
        MethodNode cl = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        cl.instructions = new InsnList();
        cl.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(cl);
        return cl;
    }
}
