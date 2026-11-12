package com.example.jjsmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(JJSMod.MODID)
public class JJSMod {
    public static final String MODID = "jjsmod";

    public JJSMod(IEventBus modBus, ModContainer container) {
        // events register themselves through @EventBusSubscriber
    }
}
