package nf.tr;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

final class Engine {

    private final Config cfg;
    private final Scan scan;
    private final Rename rename;
    private final byte[] key;
    private final byte[] keyA;
    private final byte[] keyB;
    private Long entryId;

    Engine(Config cfg) throws Exception {
        this.cfg = cfg;
        this.scan = new Scan(cfg);
        this.rename = new Rename(cfg);
        this.key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.keyA = new byte[32];
        this.keyB = new byte[32];
        new SecureRandom().nextBytes(keyB);
        for (int i = 0; i < 32; i++) {
            keyA[i] = (byte) (key[i] ^ keyB[i]);
        }
    }

    void run(Path in, Path out) throws Exception {
        if (!cfg.rename && !cfg.str && !cfg.cff && !cfg.scatter && !cfg.guard) {
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[+] shield off " + out);
            return;
        }
        Map<String, byte[]> raw = readJar(in);
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : raw.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, ClassReader.EXPAND_FRAMES);
            nodes.put(cn.name, cn);
        }

        for (String name : nodes.keySet()) {
            if (scan.innerClass(name)) {
                rename.registerInner(name);
            } else if (scan.rename(name) || scan.protectName(name)) {
                rename.register(name, scan.rename(name) || scan.protectName(name));
            }
        }
        for (String name : nodes.keySet()) {
            if (scan.innerClass(name)) rename.registerInner(name);
        }

        Map<String, byte[]> blobs = new LinkedHashMap<>();
        Map<String, byte[]> names = new LinkedHashMap<>();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        int vault = 0;

        for (Map.Entry<String, ClassNode> e : nodes.entrySet()) {
            String name = e.getKey();
            ClassNode cn = e.getValue();

            if (!name.startsWith("noface/")) {
                byte[] orig = raw.get(name + ".class");
                if (orig != null) classes.put(name + ".class", orig);
                continue;
            }

            if (scan.innerClass(name)) {
                ClassNode mapped = rename.apply(cn);
                if (cfg.str && scan.canStr(mapped)) StrEnc.apply(mapped);
                if (cfg.cff) Cff.apply(mapped);
                classes.put(mapped.name + ".class", write(mapped));
                continue;
            }

            if (scan.protectClass(cn)) {
                vault(cn, blobs, names, classes);
                vault++;
                continue;
            }

            if (scan.passRemap(name)) {
                ClassNode mapped = rename.apply(cn);
                classes.put(mapped.name + ".class", write(mapped));
                continue;
            }

            ClassNode mapped = rename.apply(cn);
            if (cfg.str && scan.canStr(mapped)) StrEnc.apply(mapped);
            classes.put(mapped.name + ".class", write(mapped));
        }

        Manifest mf = readManifest(in);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out), mf)) {
            Set<String> done = new HashSet<>();
            for (Map.Entry<String, byte[]> c : classes.entrySet()) {
                put(jos, c.getKey(), c.getValue(), done);
            }
            for (Map.Entry<String, byte[]> e : raw.entrySet()) {
                if (done.contains(e.getKey()) || e.getKey().endsWith(".class")) continue;
                if (nativeBlob(e.getKey())) {
                    put(jos, "assets/wsvc/core.dat", seal(e.getValue()), done);
                    continue;
                }
                put(jos, e.getKey(), e.getValue(), done);
            }
            for (Map.Entry<String, byte[]> b : blobs.entrySet()) {
                put(jos, b.getKey(), b.getValue(), done);
            }
            for (Map.Entry<String, byte[]> n : names.entrySet()) {
                put(jos, n.getKey(), n.getValue(), done);
            }
            put(jos, "META-INF/nf/a.bin", keyA, done);
            put(jos, "META-INF/nf/b.bin", keyB, done);
            if (entryId != null) {
                String hex = String.format("%016x", entryId);
                put(jos, "META-INF/nf/e.txt", hex.getBytes(java.nio.charset.StandardCharsets.UTF_8), done);
            }
            byte[] pad = new byte[256 + new SecureRandom().nextInt(2048)];
            new SecureRandom().nextBytes(pad);
            put(jos, String.format("META-INF/wsvc/%016x.pad", System.nanoTime()), pad, done);
            addRt(jos, done);
        }

        System.out.println("[*] vault classes: " + vault);
        System.out.println("[+] " + out);
    }

    private void vault(ClassNode cn, Map<String, byte[]> blobs, Map<String, byte[]> names, Map<String, byte[]> classes) {
        ClassNode mapped = rename.apply(cn);
        String stub = mapped.name;
        long id = rename.id(stub);
        if (cfg.entry != null && !cfg.entry.isEmpty() && cn.name.equals(cfg.entry)) {
            entryId = id;
        }

        ClassNode hidden = clone(mapped);
        String hid = rename.hiddenName(stub);
        ClassNode self = new ClassNode();
        hidden.accept(new ClassRemapper(self, new SimpleRemapper(stub, hid)));
        self.name = hid;
        if (cfg.str && scan.canStr(self)) StrEnc.apply(self);
        if (cfg.cff) Cff.apply(self);
        byte[] impl = write(self);
        byte[] sealed = seal(impl);
        if (cfg.scatter) {
            blobs.putAll(Scatter.pack(id, sealed));
        } else {
            blobs.put(String.format("META-INF/nf/v/%016x.bin", id), sealed);
        }
        names.put(String.format("META-INF/nf/n/%016x.txt", id), hid.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        StubGen.stub(mapped, id, scan);
        injectAnchor(mapped, id);
        if (cfg.str && scan.canStr(mapped)) StrEnc.apply(mapped);
        classes.put(stub + ".class", write(mapped));
    }

    private byte[] seal(byte[] plain) {
        return Seal.pack(plain, key);
    }

    private static boolean nativeBlob(String name) {
        String n = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        return n.equals("abe/abe_extractor_amd64.bin")
                || n.endsWith("/abe_extractor_amd64.bin")
                || n.equals("abe_extractor_amd64.bin");
    }

    private static ClassNode clone(ClassNode src) {
        ClassNode c = new ClassNode();
        src.accept(c);
        return c;
    }

    private static byte[] write(ClassNode cn) {
        cn.sourceFile = null;
        cn.sourceDebug = null;
        open(cn);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static void injectAnchor(ClassNode stub, long id) {
        MethodNode cl = null;
        for (MethodNode mn : stub.methods) {
            if ("<clinit>".equals(mn.name)) {
                cl = mn;
                break;
            }
        }
        if (cl == null) {
            cl = new MethodNode(org.objectweb.asm.Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            cl.instructions = new org.objectweb.asm.tree.InsnList();
            stub.methods.add(cl);
        }
        if (cl.instructions == null) cl.instructions = new org.objectweb.asm.tree.InsnList();
        org.objectweb.asm.tree.InsnList il = new org.objectweb.asm.tree.InsnList();
        il.add(new org.objectweb.asm.tree.LdcInsnNode(id));
        il.add(new org.objectweb.asm.tree.LdcInsnNode(stub.name.replace('/', '.')));
        il.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "nf/rt/Vault", "mapAnchor", "(JLjava/lang/String;)V", false));
        cl.instructions.insert(il);
    }

    private static void open(ClassNode cn) {
        for (var mn : cn.methods) {
            if ("<init>".equals(mn.name)) continue;
            mn.access = (mn.access & ~(org.objectweb.asm.Opcodes.ACC_PRIVATE | org.objectweb.asm.Opcodes.ACC_PROTECTED))
                    | org.objectweb.asm.Opcodes.ACC_PUBLIC;
        }
        for (var fn : cn.fields) {
            fn.access = (fn.access & ~(org.objectweb.asm.Opcodes.ACC_PRIVATE | org.objectweb.asm.Opcodes.ACC_PROTECTED))
                    | org.objectweb.asm.Opcodes.ACC_PUBLIC;
        }
    }

    private static Map<String, byte[]> readJar(Path jar) throws IOException {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry en;
            while ((en = jis.getNextJarEntry()) != null) {
                if (en.isDirectory()) continue;
                map.put(en.getName(), jis.readAllBytes());
            }
        }
        return map;
    }

    private static Manifest readManifest(Path jar) throws IOException {
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
            Manifest m = jis.getManifest();
            return m == null ? new Manifest() : m;
        }
    }

    private static void put(JarOutputStream jos, String name, byte[] data, Set<String> done) throws IOException {
        if (done.contains(name) || data == null) return;
        jos.putNextEntry(new JarEntry(name));
        jos.write(data);
        jos.closeEntry();
        done.add(name);
    }

    private static void addRt(JarOutputStream jos, Set<String> done) throws IOException {
        String[] rt = {
                "nf/rt/Cry.class", "nf/rt/Key.class", "nf/rt/Vault.class", "nf/rt/Gate.class",
                "nf/rt/Str.class", "nf/rt/Frag.class", "nf/rt/Guard.class", "nf/rt/Pack.class",
                "nf/rt/Launch.class"
        };
        ClassLoader cl = nf.rt.Gate.class.getClassLoader();
        for (String r : rt) {
            if (done.contains(r)) continue;
            try (InputStream in = cl.getResourceAsStream(r)) {
                if (in == null) throw new IOException("rt missing " + r);
                put(jos, r, in.readAllBytes(), done);
            }
        }
    }
}
