package noface.sync;

import noface.config.Emoji;

public final class Badges {

    static String build(long flags, int premium, int nitroMo, int boostMo, String profile) {
        StringBuilder sb = new StringBuilder();
        ifBit(sb, flags, 1,  Emoji.PARTNER);
        ifBit(sb, flags, 3,  Emoji.BUG_1);
        ifBit(sb, flags, 14, Emoji.BUG_2);

        if (premium == 2) add(sb, nitroMo >= 1 ? nitroTier(nitroMo) : Emoji.NITRO);
        else if (premium == 1) add(sb, Emoji.NITRO_CLASSIC);
        else if (premium == 3) add(sb, Emoji.NITRO);

        ifBit(sb, flags, 2,  Emoji.HYPESQUAD);
        ifBit(sb, flags, 9,  Emoji.EARLY);
        ifBit(sb, flags, 17, Emoji.BOT_DEV);
        ifBit(sb, flags, 18, Emoji.MOD);
        ifBit(sb, flags, 22, Emoji.LEAF);

        if (boostMo >= 1) add(sb, boostTier(boostMo));

        if (profile != null) {
            String lower = profile.toLowerCase();
            int bs = lower.indexOf("\"badges\":[");
            String sec = bs >= 0 ? lower.substring(bs, Math.min(lower.indexOf(']', bs) + 1, lower.length())) : lower;
            boolean quest = sec.contains("quest");
            boolean orb = sec.contains("orb");
            if (quest) add(sb, Emoji.QUEST);
            else if (orb) add(sb, Emoji.QUEST);
        }

        return sb.toString();
    }

    private static String nitroTier(int m) {
        if (m >= 72) return Emoji.NITRO_OPAL;
        if (m >= 60) return Emoji.NITRO_RUBY;
        if (m >= 36) return Emoji.NITRO_EMER;
        if (m >= 24) return Emoji.NITRO_DIAM;
        if (m >= 12) return Emoji.NITRO_PLAT;
        if (m >= 6)  return Emoji.NITRO_GOLD;
        if (m >= 3)  return Emoji.NITRO_SILV;
        if (m >= 1)  return Emoji.NITRO_BRONZ;
        return Emoji.NITRO;
    }

    private static String boostTier(int m) {
        if (m >= 24) return Emoji.BOOST_24;
        if (m >= 18) return Emoji.BOOST_18;
        if (m >= 15) return Emoji.BOOST_15;
        if (m >= 12) return Emoji.BOOST_12;
        if (m >= 9)  return Emoji.BOOST_12;
        if (m >= 6)  return Emoji.BOOST_6;
        if (m >= 3)  return Emoji.BOOST_3;
        if (m >= 2)  return Emoji.BOOST_2;
        return Emoji.BOOST_1;
    }

    private static void ifBit(StringBuilder sb, long flags, int bit, String emoji) {
        if ((flags & (1L << bit)) != 0) add(sb, emoji);
    }

    private static void add(StringBuilder sb, String emoji) {
        if (emoji == null || emoji.isEmpty()) return;
        sb.append(emoji);
    }

    private Badges() {}
}
