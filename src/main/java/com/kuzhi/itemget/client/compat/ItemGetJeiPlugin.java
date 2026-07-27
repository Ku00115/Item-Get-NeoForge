package com.kuzhi.itemget.client.compat;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.ClientHooks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class ItemGetJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ClientHooks.setJeiRuntime(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        ClientHooks.setJeiRuntime(null);
    }
}
