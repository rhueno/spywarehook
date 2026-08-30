package com.zombiesurvival.mod;

public final class Horde {

    private static int wave;

    public static void tick() {
        wave = Math.max(1, wave);
    }

    public static int wave() {
        return wave;
    }

    private Horde() {}
}
