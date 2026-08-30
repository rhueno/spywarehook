package nf.rt;

public final class Pack {

    private Pack() {}

    public static byte[] open(byte[] blob) {
        return Cry.open(blob, Key.get());
    }
}
