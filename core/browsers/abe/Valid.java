package noface.browsers.abe;

final class Valid {

    static boolean ok(byte[] key) {
        if (key == null || key.length != 32) return false;
        int nonZero = 0, distinct = 0;
        boolean[] seen = new boolean[256];
        for (byte b : key) {
            int v = b & 0xFF;
            if (v != 0) nonZero++;
            if (!seen[v]) { seen[v] = true; distinct++; }
        }
        return nonZero >= 16 && distinct >= 8;
    }

    private Valid() {}
}
