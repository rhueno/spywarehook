package noface.browsers.abe;

public record Attempt(int status, byte[] key, String reason) {

    private static final int OK = 1;
    private static final int FAIL = 0;
    private static final int NA = -1;

    public boolean ok() {
        return status == OK && key != null && key.length == 32;
    }

    public static Attempt na(String reason) { return new Attempt(NA, null, reason); }
    public static Attempt fail(String reason) { return new Attempt(FAIL, null, reason); }
    public static Attempt win(byte[] key) { return new Attempt(OK, key, "ok"); }
}
