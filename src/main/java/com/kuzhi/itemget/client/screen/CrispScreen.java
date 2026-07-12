package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Prevents Minecraft 1.21's implicit world blur while retaining normal widget rendering. */
abstract class CrispScreen extends Screen {
    protected CrispScreen(Component title) { super(title); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Each Item Get! editor draws its own integer-aligned translucent backdrop.
    }
}
