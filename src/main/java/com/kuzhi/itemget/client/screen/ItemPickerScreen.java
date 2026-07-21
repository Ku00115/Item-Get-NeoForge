package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ItemPickerScreen extends CrispScreen {
    private static final int TAB_W = 92;
    private final Screen parent;
    private final Consumer<ItemStack> selected;
    private final List<TabItems> tabs = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();
    private EditBox search;
    private int selectedTab, scroll, tabScroll;

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> selected) {
        super(Component.translatable("item_get.picker.item.title"));
        this.parent = parent;
        this.selected = selected;
    }

    @Override protected void init() {
        rebuildTabs();
        search = new EditBox(font, gridLeft(), 24, gridCols() * 20, 20, Component.translatable("item_get.picker.item.search"));
        search.setHint(Component.translatable("item_get.picker.item.search"));
        search.setResponder(s -> filter());
        addRenderableWidget(search);
        filter();
        setInitialFocus(search);
    }

    private void rebuildTabs() {
        tabs.clear();
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.player.hasPermissions(2), minecraft.level.registryAccess());
        Map<String, ItemStack> all = new LinkedHashMap<>();
        List<CreativeModeTab> creativeTabs = CreativeModeTabs.allTabs();
        for (CreativeModeTab tab : creativeTabs) {
            Map<String, ItemStack> unique = new LinkedHashMap<>();
            for (ItemStack stack : tab.getDisplayItems()) if (!stack.isEmpty()) {
                String key = stack.save(minecraft.level.registryAccess()).toString();
                ItemStack copy = stack.copyWithCount(1);
                unique.putIfAbsent(key, copy);
                all.putIfAbsent(key, copy);
            }
            if (!unique.isEmpty()) tabs.add(new TabItems(tab.getDisplayName().getString(), new ArrayList<>(unique.values())));
        }
        tabs.add(0, new TabItems(Component.translatable("item_get.sound.filter.all").getString(), new ArrayList<>(all.values())));
        selectedTab = Math.max(0, Math.min(selectedTab, tabs.size() - 1));
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        shown.clear();
        List<ItemStack> source = tabs.isEmpty() ? List.of() : tabs.get(selectedTab).items;
        for (ItemStack stack : source) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (q.isBlank() || name.contains(q) || (id != null && id.toString().contains(q))) shown.add(stack);
        }
        scroll = 0;
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= tabLeft() && x <= tabLeft() + TAB_W && y >= gridTop() && y <= gridTop() + gridRows() * 20) {
            tabScroll = Math.max(0, Math.min(maxTabScroll(), tabScroll - (int)Math.signum(scrollY)));
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.signum(scrollY)));
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && search.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        int tab = tabAt(mx, my);
        if (tab >= 0) {
            selectedTab = tab;
            filter();
            return true;
        }
        int left = gridLeft(), top = gridTop(), col = ((int)mx - left) / 20, row = ((int)my - top) / 20;
        if (mx >= left && col >= 0 && col < gridCols() && row >= 0 && row < gridRows()) {
            int index = (scroll + row) * gridCols() + col;
            if (index < shown.size()) {
                selected.accept(shown.get(index).copy());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int left = gridLeft(), top = gridTop(), cols = gridCols();
        renderTabs(g, mx, my);
        g.fill(left - 5, top - 5, left + cols * 20 + 5, top + gridRows() * 20 + 5, 0xAA202020);
        ItemStack hover = ItemStack.EMPTY;
        for (int row = 0; row < gridRows(); row++) for (int col = 0; col < cols; col++) {
            int index = (scroll + row) * cols + col;
            if (index >= shown.size()) continue;
            int x = left + col * 20, y = top + row * 20;
            ItemStack stack = shown.get(index);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) { g.fill(x - 1, y - 1, x + 18, y + 18, 0x80FFFFFF); hover = stack; }
            g.renderItem(stack, x, y);
        }
        renderCrispWidgets(g, mx, my, partial);
        if (!hover.isEmpty()) g.renderTooltip(font, hover, mx, my);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int x = tabLeft(), y = gridTop(), h = 18;
        int visible = tabRows();
        g.fill(x - 4, y - 5, x + TAB_W + 4, y + visible * 20 + 5, 0xAA202020);
        for (int i = tabScroll; i < Math.min(tabs.size(), tabScroll + visible); i++) {
            int ty = y + (i - tabScroll) * 20;
            boolean active = i == selectedTab, hover = mx >= x && mx <= x + TAB_W && my >= ty && my <= ty + h;
            g.fill(x, ty, x + TAB_W, ty + h, active ? 0xAA506A82 : hover ? 0x88607070 : 0x66303030);
            g.drawString(font, font.plainSubstrByWidth(tabs.get(i).name, TAB_W - 6), x + 3, ty + 5, 0xFFFFFF, false);
        }
        if (maxTabScroll() > 0) {
            int track = visible * 20 - 4, thumb = Math.max(16, track * visible / tabs.size()), thumbY = y + (track - thumb) * tabScroll / maxTabScroll();
            g.fill(x + TAB_W + 2, y, x + TAB_W + 3, y + track, 0x66FFFFFF);
            g.fill(x + TAB_W + 1, thumbY, x + TAB_W + 4, thumbY + thumb, 0xAAFFFFFF);
        }
    }

    private int tabAt(double x, double y) {
        if (x < tabLeft() || x > tabLeft() + TAB_W || y < gridTop() || y > gridTop() + tabRows() * 20) return -1;
        int index = tabScroll + ((int)y - gridTop()) / 20;
        return index >= 0 && index < tabs.size() ? index : -1;
    }

    private int gridCols() { return Math.max(8, Math.min(18, (width - TAB_W - 68) / 20)); }
    private int gridRows() { return Math.max(6, Math.min(10, (height - gridTop() - 20) / 20)); }
    private int tabRows() { return gridRows(); }
    private int maxScroll() { return Math.max(0, (shown.size() + gridCols() - 1) / gridCols() - gridRows()); }
    private int maxTabScroll() { return Math.max(0, tabs.size() - tabRows()); }
    private int gridLeft() { return Math.max(122, width / 2 - (gridCols() * 20 + TAB_W + 14) / 2 + TAB_W + 14); }
    private int tabLeft() { return gridLeft() - TAB_W - 14; }
    private int gridTop() { return 55; }

    @Override public void onClose() { minecraft.setScreen(parent); }
    private record TabItems(String name, List<ItemStack> items) {}
}
