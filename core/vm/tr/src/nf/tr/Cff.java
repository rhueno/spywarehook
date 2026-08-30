package nf.tr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.concurrent.ThreadLocalRandom;

final class Cff {

    private Cff() {}

    static void apply(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if (!eligible(mn)) continue;
            wrap(mn);
        }
    }

    private static boolean eligible(MethodNode mn) {
        if (mn.instructions == null || mn.instructions.size() < 8) return false;
        if (mn.instructions.size() > 500) return false;
        if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) return false;
        int acc = mn.access;
        if ((acc & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_BRIDGE)) != 0) return false;
        if (mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty()) return false;
        return true;
    }

    private static void wrap(MethodNode mn) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int key = rnd.nextInt(0x10000, 0x3FFFFFFF);
        int mask = rnd.nextInt(0x10000, 0x3FFFFFFF);
        int fake = rnd.nextInt(0x10000, 0x3FFFFFFF);
        while (fake == (key ^ mask)) fake = rnd.nextInt(0x10000, 0x3FFFFFFF);
        int slot = 64 + rnd.nextInt(32);

        LabelNode loop = new LabelNode();
        LabelNode body = new LabelNode();
        LabelNode dead = new LabelNode();
        LabelNode dflt = new LabelNode();

        InsnList orig = mn.instructions;
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(key));
        il.add(new VarInsnNode(Opcodes.ISTORE, slot));
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, slot));
        il.add(new LdcInsnNode(mask));
        il.add(new InsnNode(Opcodes.IXOR));
        int[] keys = new int[] {key ^ mask, fake};
        LabelNode[] labs = new LabelNode[] {body, dead};
        if (keys[0] > keys[1]) {
            int t = keys[0];
            keys[0] = keys[1];
            keys[1] = t;
            LabelNode tl = labs[0];
            labs[0] = labs[1];
            labs[1] = tl;
        }
        il.add(new LookupSwitchInsnNode(dflt, keys, labs));
        il.add(body);
        il.add(orig);
        il.add(dead);
        il.add(new LdcInsnNode(key));
        il.add(new VarInsnNode(Opcodes.ISTORE, slot));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(dflt);
        il.add(new LdcInsnNode(key));
        il.add(new VarInsnNode(Opcodes.ISTORE, slot));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        mn.instructions = il;
        mn.localVariables = null;
        mn.parameters = null;
    }
}
