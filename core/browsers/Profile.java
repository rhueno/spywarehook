package noface.browsers;

import java.nio.file.Path;

public record Profile(
        String browser,
        String name,
        Path dir,
        byte[] masterKey,
        byte[] abeKey
) {
    public Profile(String browser, String name, Path dir, byte[] masterKey) {
        this(browser, name, dir, masterKey, null);
    }
}
