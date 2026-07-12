package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ItemPickerScreen extends CrispScreen {
    private final Screen parent; private final Consumer<ItemStack> selected;
    private final List<ItemStack> all = new ArrayList<>(), shown = new ArrayList<>();
    private EditBox search; private int scroll;

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> selected) { super(Component.translatable("item_get.picker.item.title")); this.parent = parent; this.selected = selected; }
    @Override protected void init() {
        all.clear();
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.player.hasPermissions(2), minecraft.level.registryAccess());
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) for (ItemStack stack : tab.getDisplayItems()) {
            if (!stack.isEmpty()) unique.putIfAbsent(stack.save(minecraft.level.registryAccess()).toString(), stack.copyWithCount(1));
        }
        all.addAll(unique.values());
        search = new EditBox(font, width / 2 - 120, 24, 240, 20, Component.translatable("item_get.picker.item.search")); search.setHint(Component.translatable("item_get.picker.item.search")); search.setResponder(s -> filter()); addRenderableWidget(search); filter(); setInitialFocus(search);
    }
    private void filter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT); shown.clear();
        for (ItemStack stack : all) { ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem()); String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT); if (q.isBlank() || name.contains(q) || (id != null && id.toString().contains(q))) shown.add(stack); }
        scroll = 0;
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) { scroll = Math.max(0, Math.min(Math.max(0, (shown.size() - 1) / 10 - 5), scroll - (int)Math.signum(scrollY))); return true; }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && search.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        int left = width / 2 - 100, top = 57, col = ((int)mx - left) / 20, row = ((int)my - top) / 20;
        if (mx >= left && col >= 0 && col < 10 && row >= 0 && row < 6) {
            int index = (scroll + row) * 10 + col;
            if (index < shown.size()) { selected.accept(shown.get(index).copy()); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000); g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int left = width / 2 - 100, top = 57; g.fill(left - 5, top - 5, left + 205, top + 125, 0xAA202020);
        ItemStack hover = ItemStack.EMPTY;
        for (int row = 0; row < 6; row++) for (int col = 0; col < 10; col++) {
            int index = (scroll + row) * 10 + col; if (index >= shown.size()) continue;
            int x = left + col * 20, y = top + row * 20; ItemStack stack = shown.get(index);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) { g.fill(x - 1, y - 1, x + 18, y + 18, 0x80FFFFFF); hover = stack; }
            g.renderItem(stack, x, y);
        }
        super.render(g, mx, my, partial); if (!hover.isEmpty()) g.renderTooltip(font, hover, mx, my);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
