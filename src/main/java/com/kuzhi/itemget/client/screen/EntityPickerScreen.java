package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class EntityPickerScreen extends CrispScreen {
    private final Screen parent; private final Consumer<String> selected;
    private final List<EntityType<?>> all = new ArrayList<>(), shown = new ArrayList<>();
    private EditBox search; private int scroll;
    public EntityPickerScreen(Screen parent, Consumer<String> selected) { super(Component.translatable("item_get.picker.entity.title")); this.parent = parent; this.selected = selected; }
    @Override protected void init() {
        all.clear(); BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityType::canSummon)
                .sorted(Comparator.comparing(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e).toString())).forEach(all::add);
        search = new EditBox(font, width / 2 - 140, 25, 280, 20, Component.translatable("item_get.picker.entity.search"));
        search.setHint(Component.translatable("item_get.picker.entity.search")); search.setResponder(v -> filter()); addRenderableWidget(search); filter(); setInitialFocus(search);
    }
    private void filter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT); shown.clear();
        for (EntityType<?> type : all) { String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(); String name = type.getDescription().getString().toLowerCase(Locale.ROOT); if (q.isBlank() || id.contains(q) || name.contains(q)) shown.add(type); }
        scroll = 0;
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) { scroll = Math.max(0, Math.min(Math.max(0, shown.size() - 8), scroll - (int)Math.signum(scrollY))); return true; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (search != null && search.isMouseOver(x, y)) return super.mouseClicked(x, y, button);
        int top = 54, row = ((int)y - top) / 24;
        if (x >= width / 2 - 140 && x <= width / 2 + 140 && row >= 0 && row < 8 && scroll + row < shown.size()) {
            selected.accept(BuiltInRegistries.ENTITY_TYPE.getKey(shown.get(scroll + row)).toString()); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000); g.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF); int top = 54;
        for (int i = 0; i < 8 && scroll + i < shown.size(); i++) { EntityType<?> type = shown.get(scroll + i); int y = top + i * 24;
            g.fill(width / 2 - 140, y, width / 2 + 140, y + 21, 0x99303030);
            String name = type.getDescription().getString(); String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            g.drawString(font, name, width / 2 - 132, y + 6, 0xFFFFFF);
            int available = 256 - font.width(name) - 12;
            if (available > 35) { String visible = font.plainSubstrByWidth(id, available); g.drawString(font, visible, width / 2 + 132 - font.width(visible), y + 6, 0xFFFFFF); }
        } renderCrispWidgets(g, mx, my, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
