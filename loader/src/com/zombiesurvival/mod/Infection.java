package com.zombiesurvival.mod;

public final class Infection {

    private static boolean hot;

    public static void spread() {
        hot = true;
    }

    public static boolean hot() {
        return hot;
    }

    private Infection() {}
}
