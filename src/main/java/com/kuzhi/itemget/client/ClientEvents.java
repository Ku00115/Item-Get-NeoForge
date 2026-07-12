package com.kuzhi.itemget.client;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.network.RequestRulesPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.item_get.manager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.item_get");
    public static final KeyMapping CLOSE = new KeyMapping("key.item_get.close_reminder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.item_get");

    @EventBusSubscriber(modid = ItemGet.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(CLOSE); }
    }

    @EventBusSubscriber(modid = ItemGet.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            AudioHelper.tick();
            ClientHooks.tick();
            if (OPEN.consumeClick()) ItemGetNetwork.requestRules();
        }
    }
}
