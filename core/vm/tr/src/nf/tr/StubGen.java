package nf.tr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class StubGen {

    private static final String GATE = "nf/rt/Gate";
    private static final String CALL = "call";
    private static final String CALL_DESC = "(JLjava/lang/String;Ljava/lang/String;Z[Ljava/lang/Object;)Ljava/lang/Object;";

    private StubGen() {}

    static void stub(ClassNode cn, long id, Scan scan) {
        for (MethodNode mn : cn.methods) {
            if (!scan.stubMethod(cn, mn)) continue;
            replace(cn, mn, id);
        }
        injectClinit(cn, id);
    }

    private static void injectClinit(ClassNode cn, long id) {
        MethodNode cl = null;
        for (MethodNode mn : cn.methods) {
            if ("<clinit>".equals(mn.name)) {
                cl = mn;
                break;
            }
        }
        if (cl == null) {
            cl = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            cl.instructions = new InsnList();
            cn.methods.add(cl);
        }
        if (cl.instructions == null) cl.instructions = new InsnList();
        InsnList pre = new InsnList();
        pre.add(new LdcInsnNode(id));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "warm", "(J)V", false));
        cl.instructions.insert(pre);
        AbstractInsnNode last = cl.instructions.getLast();
        if (last == null || last.getOpcode() != Opcodes.RETURN) {
            cl.instructions.add(new InsnNode(Opcodes.RETURN));
        }
    }

    private static void replace(ClassNode cn, MethodNode mn, long id) {
        Type[] args = Type.getArgumentTypes(mn.desc);
        Type ret = Type.getReturnType(mn.desc);
        boolean statik = (mn.access & Opcodes.ACC_STATIC) != 0;

        mn.instructions = new InsnList();
        mn.tryCatchBlocks.clear();
        mn.localVariables = null;
        mn.parameters = null;

        InsnList il = mn.instructions;
        int slot = mn.maxLocals;
        int arr = slot;
        mn.maxLocals = arr + 1;

        int n = args.length + (statik ? 0 : 1);
        il.add(new LdcInsnNode(n));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new VarInsnNode(Opcodes.ASTORE, arr));

        int idx = 0;
        int loc = 0;
        if (!statik) {
            packArg(il, arr, idx++, Opcodes.ALOAD, 0, Type.getObjectType("java/lang/Object"));
            loc = 1;
        }
        for (Type t : args) {
            packArg(il, arr, idx++, load(t), loc, t);
            loc += t.getSize();
        }

        il.add(new LdcInsnNode(id));
        il.add(new LdcInsnNode(mn.name));
        il.add(new LdcInsnNode(mn.desc));
        il.add(new InsnNode(statik ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ALOAD, arr));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, CALL, CALL_DESC, false));
        unboxReturn(il, ret);
    }

    private static void packArg(InsnList il, int arr, int idx, int load, int local, Type t) {
        il.add(new VarInsnNode(Opcodes.ALOAD, arr));
        il.add(pushInt(idx));
        il.add(new VarInsnNode(load, local));
        box(il, t);
        il.add(new InsnNode(Opcodes.AASTORE));
    }

    private static AbstractInsnNode pushInt(int v) {
        return switch (v) {
            case 0 -> new InsnNode(Opcodes.ICONST_0);
            case 1 -> new InsnNode(Opcodes.ICONST_1);
            case 2 -> new InsnNode(Opcodes.ICONST_2);
            case 3 -> new InsnNode(Opcodes.ICONST_3);
            case 4 -> new InsnNode(Opcodes.ICONST_4);
            case 5 -> new InsnNode(Opcodes.ICONST_5);
            default -> new LdcInsnNode(v);
        };
    }

    private static int load(Type t) {
        return switch (t.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.ILOAD;
            case Type.LONG -> Opcodes.LLOAD;
            case Type.FLOAT -> Opcodes.FLOAD;
            case Type.DOUBLE -> Opcodes.DLOAD;
            default -> Opcodes.ALOAD;
        };
    }

    private static void box(InsnList il, Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
            case Type.BYTE -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
            case Type.CHAR -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
            case Type.SHORT -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
            case Type.INT -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
            case Type.LONG -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
            case Type.FLOAT -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
            case Type.DOUBLE -> il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
            default -> {}
        }
    }

    private static void unboxReturn(InsnList il, Type ret) {
        if (ret.getSort() == Type.VOID) {
            il.add(new InsnNode(Opcodes.POP));
            il.add(new InsnNode(Opcodes.RETURN));
            return;
        }
        if (ret.getSort() == Type.OBJECT || ret.getSort() == Type.ARRAY) {
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, ret.getInternalName()));
            il.add(new InsnNode(Opcodes.ARETURN));
            return;
        }
        switch (ret.getSort()) {
            case Type.BOOLEAN -> {
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "toBool", "(Ljava/lang/Object;)Z", false));
                il.add(new InsnNode(Opcodes.IRETURN));
            }
            case Type.INT, Type.BYTE, Type.CHAR, Type.SHORT -> {
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "toInt", "(Ljava/lang/Object;)I", false));
                il.add(new InsnNode(Opcodes.IRETURN));
            }
            case Type.LONG -> {
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false));
                il.add(new InsnNode(Opcodes.LRETURN));
            }
            case Type.FLOAT -> {
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false));
                il.add(new InsnNode(Opcodes.FRETURN));
            }
            case Type.DOUBLE -> {
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false));
                il.add(new InsnNode(Opcodes.DRETURN));
            }
            default -> il.add(new InsnNode(Opcodes.ARETURN));
        }
    }
}
