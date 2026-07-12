package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.AudioHelper;
import com.kuzhi.itemget.client.CustomSoundLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class SoundPickerScreen extends CrispScreen {
    private enum Filter { ALL, ITEM_GET, VANILLA, MOD, CUSTOM; String label() { return Component.translatable("item_get.sound.filter."+name().toLowerCase(Locale.ROOT)).getString(); } }
    private final Screen parent; private final Consumer<String> selected;
    private final List<String> all = new ArrayList<>(), shown = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private EditBox search; private Button filterButton; private Filter filter = Filter.ALL; private int scroll; private String status = "";

    public SoundPickerScreen(Screen parent, Consumer<String> selected) { super(Component.translatable("item_get.sound.title")); this.parent = parent; this.selected = selected; }
    @Override protected void init() {
        all.clear(); BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).sorted().forEach(all::add); all.addAll(CustomSoundLibrary.refresh()); all.sort(Comparator.naturalOrder());
        int left = width / 2 - 165;
        filterButton = addRenderableWidget(Button.builder(Component.translatable("item_get.sound.filter",filter.label()), b -> { filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length]; b.setMessage(Component.translatable("item_get.sound.filter",filter.label())); applyFilter(); }).bounds(left, 25, 96, 20).build());
        search = new EditBox(font, left + 100, 25, 230, 20, Component.translatable("item_get.sound.search")); search.setHint(Component.translatable("item_get.sound.search")); search.setResponder(v -> applyFilter()); addRenderableWidget(search); applyFilter(); setInitialFocus(search);
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT); shown.clear();
        for (String id : all) if (matchesType(id) && (q.isBlank() || id.contains(q))) shown.add(id);
        scroll = 0; rebuildRows();
    }
    private boolean matchesType(String id) { return switch (filter) { case ALL -> true; case ITEM_GET -> id.startsWith("item_get:"); case VANILLA -> id.startsWith("minecraft:"); case CUSTOM -> CustomSoundLibrary.isCustom(id); case MOD -> !id.startsWith("minecraft:") && !id.startsWith("item_get:") && !CustomSoundLibrary.isCustom(id); }; }
    private int visibleRows() { return Math.max(4, Math.min(12, (height - 72) / 23)); }

    private void rebuildRows() {
        rows.forEach(this::removeWidget); rows.clear(); int left = width / 2 - 165, top = 53;
        for (int i = 0; i < visibleRows() && scroll + i < shown.size(); i++) {
            String id = shown.get(scroll + i); int y = top + i * 23;
            Button choose = Button.builder(Component.literal(displayName(id)), b -> { selected.accept(id); minecraft.setScreen(parent); }).bounds(left, y, 278, 20).build();
            Button preview = Button.builder(Component.translatable("item_get.sound.preview"), b -> status = Component.translatable(AudioHelper.play(id)?"item_get.sound.playing":"item_get.sound.failed",id).getString()).bounds(left + 282, y, 48, 20).build();
            rows.add(choose); rows.add(preview); addRenderableWidget(choose); addRenderableWidget(preview);
        }
    }

    private static String displayName(String id) { return switch (id) {
        case "item_get:item_acquired" -> Component.translatable("item_get.sound.name.classic",id).getString();
        case "item_get:item_acquired_soft" -> Component.translatable("item_get.sound.name.soft",id).getString();
        case "item_get:item_acquired_rare" -> Component.translatable("item_get.sound.name.rare",id).getString();
        case "item_get:item_acquired_mystic" -> Component.translatable("item_get.sound.name.mystic",id).getString();
        case "item_get:item_acquired_mechanical" -> Component.translatable("item_get.sound.name.mechanical",id).getString();
        case "item_get:item_acquired_arcade" -> Component.translatable("item_get.sound.name.arcade",id).getString();
        case "item_get:item_acquired_relic" -> Component.translatable("item_get.sound.name.relic",id).getString();
        default -> id;
    }; }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) { int old = scroll; scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int)Math.signum(scrollY))); if (old != scroll) rebuildRows(); return true; }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000); g.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF); renderCrispWidgets(g, mx, my, partial);
        if (!status.isBlank()) g.drawCenteredString(font, status, width / 2, height - 12, 0x999999);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
