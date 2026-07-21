package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class DescriptionEditScreen extends CrispScreen {
    private static final int DESCRIPTION_LIMIT = 2048;
    private final Screen parent;
    private final String initial;
    private final Consumer<String> saved;
    private EditBox value;

    public DescriptionEditScreen(Screen parent, String initial, Consumer<String> saved) {
        super(Component.translatable("item_get.editor.description_title"));
        this.parent = parent;
        this.initial = initial == null ? "" : initial;
        this.saved = saved;
    }

    @Override
    protected void init() {
        int boxW = Math.min(width - 40, 360);
        int x = (width - boxW) / 2;
        int y = Math.max(44, height / 2 - 34);
        value = new EditBox(font, x, y, boxW, 20, Component.translatable("item_get.editor.description"));
        value.setMaxLength(DESCRIPTION_LIMIT);
        value.setValue(initial);
        addRenderableWidget(value);

        int half = (boxW - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            saved.accept(value.getValue());
            minecraft.setScreen(parent);
        }).bounds(x, y + 28, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(x + half + 4, y + 28, half, 20).build());
        setInitialFocus(value);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        int boxW = Math.min(width - 40, 360);
        int x = (width - boxW) / 2;
        int y = Math.max(44, height / 2 - 34);
        g.drawCenteredString(font, title, width / 2, y - 25, 0xFFFFFF);
        g.drawString(font, Component.translatable("item_get.editor.description_hint"), x, y - 11, 0xAFC4D8);
        renderCrispWidgets(g, mx, my, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
