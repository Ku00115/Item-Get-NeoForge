package com.kuzhi.itemget;

import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ItemGet.MOD_ID)
public final class ItemGet {
    public static final String MOD_ID = "item_get";

    public ItemGet(IEventBus modBus) {
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ItemGetNetwork::register);
        NeoForge.EVENT_BUS.register(new ServerEvents());
    }
}
