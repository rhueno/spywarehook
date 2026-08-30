package nf.rt;

final class Key {

    private static final byte[] KEY = load();

    private Key() {}

    static byte[] get() {
        return KEY.clone();
    }

    private static byte[] load() {
        try (var a = Key.class.getResourceAsStream("/META-INF/nf/a.bin");
             var b = Key.class.getResourceAsStream("/META-INF/nf/b.bin")) {
            if (a == null || b == null) throw new IllegalStateException("key missing");
            byte[] p1 = a.readAllBytes();
            byte[] p2 = b.readAllBytes();
            byte[] out = new byte[p1.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) (p1[i] ^ p2[i]);
            }
            return out;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
