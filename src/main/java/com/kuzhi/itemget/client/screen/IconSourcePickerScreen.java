package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class IconSourcePickerScreen extends CrispScreen {
    private final Screen parent;
    private final Consumer<ItemStack> itemSelected;
    private final Consumer<String> imageSelected;

    public IconSourcePickerScreen(Screen parent, Consumer<ItemStack> itemSelected, Consumer<String> imageSelected) {
        super(Component.translatable("item_get.picker.icon_source.title"));
        this.parent = parent;
        this.itemSelected = itemSelected;
        this.imageSelected = imageSelected;
    }

    @Override
    protected void init() {
        int w = Math.min(220, width - 40);
        int x = (width - w) / 2;
        int y = height / 2 - 24;
        addRenderableWidget(Button.builder(Component.translatable("item_get.picker.icon_source.item"), b -> minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            itemSelected.accept(stack);
            minecraft.setScreen(parent);
        }))).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("item_get.picker.icon_source.image"), b -> minecraft.setScreen(new ImagePickerScreen(this, id -> {
            imageSelected.accept(id);
            minecraft.setScreen(parent);
        }))).bounds(x, y + 24, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(x, y + 48, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        g.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFF);
        renderCrispWidgets(g, mx, my, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
