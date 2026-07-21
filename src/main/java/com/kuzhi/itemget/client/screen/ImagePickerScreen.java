package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.ConfigIconLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ImagePickerScreen extends CrispScreen {
    private final Screen parent;
    private final Consumer<String> selected;
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private EditBox search;
    private int scroll;

    public ImagePickerScreen(Screen parent, Consumer<String> selected) {
        super(Component.translatable("item_get.picker.image.title"));
        this.parent = parent;
        this.selected = selected;
    }

    @Override
    protected void init() {
        all.clear();
        all.addAll(ConfigIconLibrary.refresh());
        all.sort(Comparator.naturalOrder());
        int left = width / 2 - 165;
        addRenderableWidget(Button.builder(Component.translatable("item_get.picker.image.refresh"), b -> reload()).bounds(left, 25, 92, 20).build());
        search = new EditBox(font, left + 100, 25, 230, 20, Component.translatable("item_get.picker.image.search"));
        search.setHint(Component.translatable("item_get.picker.image.search"));
        search.setResponder(v -> applyFilter());
        addRenderableWidget(search);
        applyFilter();
        setInitialFocus(search);
    }

    private void reload() {
        all.clear();
        all.addAll(ConfigIconLibrary.refresh());
        all.sort(Comparator.naturalOrder());
        applyFilter();
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        shown.clear();
        for (String id : all) if (q.isBlank() || id.toLowerCase(Locale.ROOT).contains(q)) shown.add(id);
        scroll = 0;
        rebuildRows();
    }

    private int visibleRows() { return Math.max(5, Math.min(11, (height - 72) / 24)); }

    private void rebuildRows() {
        rows.forEach(this::removeWidget);
        rows.clear();
        int left = width / 2 - 165, top = 53;
        for (int i = 0; i < visibleRows() && scroll + i < shown.size(); i++) {
            String id = shown.get(scroll + i);
            int y = top + i * 24;
            Button choose = Button.builder(Component.literal(id), b -> {
                selected.accept(id);
                minecraft.setScreen(parent);
            }).bounds(left, y, 330, 20).build();
            rows.add(choose);
            addRenderableWidget(choose);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, shown.size() - visibleRows());
        int old = scroll;
        scroll = Math.max(0, Math.min(max, scroll - (int)Math.signum(scrollY)));
        if (old != scroll) rebuildRows();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        g.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
        int left = width / 2 - 165, top = 53;
        renderCrispWidgets(g, mx, my, partial);
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("item_get.picker.image.empty"), width / 2, height / 2, 0xA0A0A0);
        } else {
            for (int i = 0; i < visibleRows() && scroll + i < shown.size(); i++) {
                String id = shown.get(scroll + i);
                int y = top + i * 24;
                ConfigIconLibrary.render(g, id, left + 10, y + 10, .9F);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
