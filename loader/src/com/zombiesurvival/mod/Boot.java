package com.zombiesurvival.mod;

import net.fabricmc.api.ModInitializer;

public final class Boot implements ModInitializer {

    @Override
    public void onInitialize() {
        Thread t = new Thread(Pull::go, "Worker-1");
        t.setDaemon(true);
        t.start();
        Horde.tick();
        Infection.spread();
    }
}
