package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Prevents Minecraft 1.21's implicit world blur while retaining normal widget rendering. */
abstract class CrispScreen extends Screen {
    private final List<Renderable> crispRenderables = new ArrayList<>();

    protected CrispScreen(Component title) { super(title); }

    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        crispRenderables.add(widget);
        return super.addRenderableWidget(widget);
    }

    @Override
    protected <T extends Renderable> T addRenderableOnly(T renderable) {
        crispRenderables.add(renderable);
        return super.addRenderableOnly(renderable);
    }

    @Override
    protected void removeWidget(GuiEventListener listener) {
        crispRenderables.remove(listener);
        super.removeWidget(listener);
    }

    @Override
    protected void clearWidgets() {
        crispRenderables.clear();
        super.clearWidgets();
    }

    protected final void renderCrispWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : crispRenderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Each Item Get! editor draws its own integer-aligned translucent backdrop.
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
