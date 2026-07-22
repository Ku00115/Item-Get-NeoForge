package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.ClientHooks;
import com.kuzhi.itemget.client.ConfigIconLibrary;
import com.kuzhi.itemget.client.SideReminderOverlay;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleConditions;
import com.kuzhi.itemget.rule.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class RuleEditorScreen extends CrispScreen {
    private static final int TITLE_LIMIT = 120;
    private final Screen parent;
    private final ReminderRule original;
    private final ReminderRule rule;
    private final Consumer<ReminderRule> saver;
    private Section section = Section.BASIC;
    private EditBox count, titleBox, subtitleBox, nbtBox;
    private final List<ConditionDraft> conditions = new ArrayList<>();
    private String conditionLogic = "AND";
    private int rootX, rootW, navX, navW, formX, formW, previewX, previewW, top, bottom, row;
    private int navScroll, previewScroll, nbtButtonX, nbtButtonY, nbtButtonSize;
    private boolean descFocused;
    private int descScroll;

    public RuleEditorScreen(Screen parent, ReminderRule rule, boolean existing, Consumer<ReminderRule> saver) {
        super(Component.translatable(existing ? "item_get.editor.edit_title" : "item_get.editor.create_title"));
        this.parent = parent;
        this.original = rule;
        this.rule = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(rule), ReminderRule.class);
        this.saver = saver;
        if (this.rule.sound == null || this.rule.sound.isBlank()) this.rule.sound = "item_get:item_acquired";
        loadConditions();
    }

    @Override protected void init() {
        layout();
        count = null;
        titleBox = null;
        subtitleBox = null;
        nbtBox = null;
        nbtButtonSize = 0;
        conditions.forEach(c -> { c.input = null; c.count = null; });

        int y = top + 30;
        int h = height < 300 ? 18 : 20;
        int gap = height < 300 ? 6 : 8;
        switch (section) {
            case BASIC -> {
                titleBox = new EditBox(font, formX, y, formW, h, Component.translatable("item_get.editor.title"));
                titleBox.setMaxLength(TITLE_LIMIT);
                titleBox.setValue(rule.title == null ? "" : rule.title);
                addRenderableWidget(titleBox);
                subtitleBox = new EditBox(font, formX, y + h + 18, formW, h, Component.translatable("item_get.editor.subtitle"));
                subtitleBox.setMaxLength(TITLE_LIMIT);
                subtitleBox.setValue(rule.subtitle == null ? "" : rule.subtitle);
                addRenderableWidget(subtitleBox);
            }
            case TRIGGER -> {
                int typeW = Math.max(78, Math.min(118, formW / 3));
                int pick = h, check = h;
                int inputW = Math.max(58, formW - typeW - pick - check - 14);
                int maxY = height - h - 48;
                for (int i = 0; i < conditions.size() && y <= maxY; i++) {
                    ConditionDraft c = conditions.get(i);
                    int index = i;
                    addRenderableWidget(Button.builder(Component.translatable(c.type.translationKey), b -> openConditionType(index)).bounds(formX, y, typeW, h).build());
                    c.input = new EditBox(font, formX + typeW + 4, y, inputW, h, Component.translatable("item_get.editor.condition_input"));
                    c.input.setMaxLength(4096);
                    c.input.setValue(conditionInput(c));
                    c.input.setHint(Component.translatable(inputHint(c.type)));
                    addRenderableWidget(c.input);
                    addRenderableWidget(Button.builder(Component.empty(), b -> openConditionTarget(index)).bounds(formX + typeW + inputW + 8, y, pick, h).build());
                    addRenderableWidget(Button.builder(Component.literal(c.selected ? "■" : "□"), b -> { read(); c.selected = !c.selected; rebuildWidgets(); }).bounds(formX + formW - check, y, check, h).build());
                    if (counted(c.type)) {
                        c.count = new EditBox(font, formX + typeW + 4, y + h + 3, 48, h, Component.translatable("item_get.editor.count"));
                        c.count.setValue(Integer.toString(threshold(c.data)));
                        addRenderableWidget(c.count);
                        y += h + 7;
                    }
                    y += h + gap;
                }
                int addW = Math.min(60, formW);
                addRenderableWidget(Button.builder(Component.literal("+"), b -> { read(); addCondition(); rebuildWidgets(); }).bounds(formX + (formW - addW) / 2, Math.min(y, maxY + h), addW, h).build());
                if (selectedConditionCount() >= 1) {
                    int by = height - h - 32, bw = Math.max(64, Math.min(100, (formW - 6) / 2)), bx = formX + (formW - bw * 2 - 6) / 2;
                    addRenderableWidget(Button.builder(Component.translatable("AND".equals(conditionLogic) ? "item_get.editor.logic_all" : "item_get.editor.logic_any"), b -> { read(); conditionLogic = "AND".equals(conditionLogic) ? "OR" : "AND"; rebuildWidgets(); }).bounds(bx, by, bw, h).build());
                    addRenderableWidget(Button.builder(Component.translatable("item_get.editor.logic_delete"), b -> { read(); deleteSelectedConditions(); rebuildWidgets(); }).bounds(bx + bw + 6, by, bw, h).build());
                }
            }
            case DISPLAY -> {
                int half = (formW - 6) / 2;
                addRenderableWidget(Button.builder(Component.translatable("item_get.editor.icon", iconName()), b -> openIconPicker()).bounds(formX, y, half, h).build());
                addRenderableWidget(Button.builder(Component.translatable("item_get.editor.display", displayName()), b -> cycleDisplay()).bounds(formX + half + 6, y, half, h).build());
                y += h + gap;
                addRenderableWidget(Button.builder(Component.translatable(rule.pauseSingleplayer ? "item_get.editor.pause_on" : "item_get.editor.pause_off"), b -> {
                    rule.pauseSingleplayer = !rule.pauseSingleplayer;
                    b.setMessage(Component.translatable(rule.pauseSingleplayer ? "item_get.editor.pause_on" : "item_get.editor.pause_off"));
                }).bounds(formX, y, formW, h).build());
            }
            case AUDIO -> addRenderableWidget(Button.builder(Component.translatable("item_get.editor.sound"), b -> {
                read();
                minecraft.setScreen(new SoundPickerScreen(this, id -> {
                    rule.sound = id;
                    minecraft.setScreen(this);
                }));
            }).bounds(formX, y, Math.min(150, formW), h).build());
            case PONDER -> addRenderableWidget(Button.builder(Component.translatable("item_get.editor.ponder", ponderName()), b -> openPonderBinding()).bounds(formX, y, formW, h).build());
            case DEBUG -> addRenderableWidget(Button.builder(Component.translatable("item_get.editor.preview"), b -> preview()).bounds(formX, y, Math.min(130, formW), h).build());
        }

        int saveY = height - h - 8;
        int half = (formW - 6) / 2;
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.save"), b -> { read(); applyDraft(); saver.accept(original); minecraft.setScreen(parent); }).bounds(formX, saveY, half, h).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(formX + half + 6, saveY, half, h).build());
    }

    private void layout() {
        top = height < 300 ? 28 : 38;
        bottom = height - 38;
        row = height < 300 ? 22 : 24;
        rootW = Math.max(300, Math.min(width - 24, 820));
        rootX = (width - rootW) / 2;
        navW = Math.min(86, Math.max(70, rootW / 9));
        int gap = width < 560 ? 8 : 12;
        previewW = Math.min(width < 560 ? 150 : 270, Math.max(120, rootW / 3));
        formW = Math.max(185, rootW - navW - previewW - gap * 2);
        navX = rootX;
        formX = navX + navW + gap;
        previewX = formX + formW + gap;
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
        if (section == Section.BASIC && descHit(x, y)) {
            descFocused = true;
            descScroll = Math.max(0, Math.min(maxDescScroll(), descScroll - (int)Math.signum(delta)));
            return true;
        }
        if (x >= navX && x <= navX + navW && y >= navTop() && y <= navBottom()) {
            navScroll = Math.max(0, Math.min(maxNavScroll(), navScroll - (int)Math.signum(delta) * row));
            return true;
        }
        if (x >= previewX && x <= previewX + previewW && y >= top && y <= bottom) {
            previewScroll = Math.max(0, Math.min(maxPreviewScroll(), previewScroll - (int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (section == Section.BASIC) {
                descFocused = descHit(x, y);
                if (descFocused) {
                    if (titleBox != null) titleBox.setFocused(false);
                    if (subtitleBox != null) subtitleBox.setFocused(false);
                    setFocused(null);
                    return true;
                }
            }
            int hit = navAt(x, y);
            if (hit >= 0) {
                read();
                section = Section.values()[hit];
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (descFocused) {
            if (rule.description == null) rule.description = "";
            if (rule.description.length() < 2048 && codePoint >= 32) rule.description += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (descFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { descFocused = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && rule.description != null && !rule.description.isEmpty()) { rule.description = rule.description.substring(0, rule.description.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { rule.description = (rule.description == null ? "" : rule.description) + "\n"; return true; }
            if (key == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) rule.description = ((rule.description == null ? "" : rule.description) + clip).substring(0, Math.min(2048, (rule.description == null ? "" : rule.description).length() + clip.length()));
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void loadConditions() {
        conditions.clear();
        conditionLogic = RuleConditions.logic(rule);
        for (RuleConditions.Entry entry : RuleConditions.entries(rule)) conditions.add(new ConditionDraft(entry.type(), entry.data()));
        if (conditions.isEmpty()) addCondition();
    }

    private void writeConditions() {
        readConditions();
        List<RuleConditions.Entry> out = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            ConditionDraft c = conditions.get(i);
            out.add(new RuleConditions.Entry(c.type, c.data.deepCopy(), false, i));
        }
        RuleConditions.write(rule, out, conditionLogic);
    }

    private void readConditions() {
        for (ConditionDraft c : conditions) {
            if (c.input != null) applyConditionInput(c, c.input.getValue().trim());
            if (c.count != null) {
                try { c.data.addProperty("count", Math.max(1, Integer.parseInt(c.count.getValue().trim()))); }
                catch (Exception ignored) { c.data.addProperty("count", 1); }
            }
        }
    }

    private void addCondition() {
        JsonObject data = new JsonObject();
        applyDefaults(TriggerType.ITEM_ACQUIRED, data);
        conditions.add(new ConditionDraft(TriggerType.ITEM_ACQUIRED, data));
    }

    private void openConditionType(int index) {
        read();
        List<SelectionListScreen.Entry> e = new ArrayList<>();
        for (TriggerType t : TriggerType.values()) e.add(new SelectionListScreen.Entry(t.name(), Component.translatable(t.translationKey).getString()));
        minecraft.setScreen(new SelectionListScreen(this, tr("item_get.editor.choose_trigger"), e, id -> {
            ConditionDraft c = conditions.get(index);
            c.type = TriggerType.valueOf(id);
            applyDefaults(c.type, c.data = new JsonObject());
            minecraft.setScreen(this);
        }));
    }

    private void openConditionTarget(int index) {
        read();
        ConditionDraft c = conditions.get(index);
        switch(c.type) {
            case ITEM_ACQUIRED -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { setItemTarget(c.data, "item", stack, true); minecraft.setScreen(this); }));
            case ENTITY_KILLED -> minecraft.setScreen(new EntityPickerScreen(this, id -> { c.data.addProperty("entity", id); minecraft.setScreen(this); }));
            case HEALTH_AT, HUNGER_AT -> minecraft.setScreen(new NumberInputScreen(this, tr(c.type == TriggerType.HEALTH_AT ? "item_get.editor.health_threshold" : "item_get.editor.hunger_threshold"), dataValue(c.data, "value", "10"), v -> { try { c.data.addProperty("value", Double.parseDouble(v)); } catch (Exception ignored) {} minecraft.setScreen(this); }));
            case EFFECT_GAINED -> openEntries(tr("item_get.editor.choose_effect"), effects(), id -> c.data.addProperty("effect", id));
            case WEATHER_IS -> openEntries(tr("item_get.editor.choose_weather"), List.of(entry("clear", choiceName("clear")), entry("rain", choiceName("rain")), entry("thunder", choiceName("thunder"))), id -> c.data.addProperty("weather", id));
            case TIME_IS -> openEntries(tr("item_get.editor.choose_time"), List.of(entry("day", choiceName("day")), entry("noon", choiceName("noon")), entry("night", choiceName("night")), entry("midnight", choiceName("midnight"))), id -> c.data.addProperty("time", id));
            case ENTER_BIOME -> openEntries(tr("item_get.editor.choose_biome"), catalog(ClientHooks.biomes(), "biome"), id -> c.data.addProperty("biome", id));
            case ENTER_STRUCTURE -> openEntries(tr("item_get.editor.choose_structure"), catalog(ClientHooks.structures(), "structure"), id -> c.data.addProperty("structure", id));
            case DEATH_BY -> openEntries(tr("item_get.editor.choose_death"), namedCatalog(ClientHooks.damageTypes(), ClientHooks::damageTypeName), id -> c.data.addProperty("death", id));
            case ADVANCEMENT_DONE -> openEntries(tr("item_get.editor.choose_advancement"), namedCatalog(ClientHooks.advancements(), ClientHooks::advancementName), id -> c.data.addProperty("advancement", id));
            case OBSERVE_BLOCK -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { c.data.addProperty("block", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); minecraft.setScreen(this); }));
            case OBSERVE_ENTITY -> minecraft.setScreen(new EntityPickerScreen(this, id -> { c.data.addProperty("entity", id); minecraft.setScreen(this); }));
            case HOVER_ITEM -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { setItemTarget(c.data, "item", stack, true); minecraft.setScreen(this); }));
        }
    }

    private void openEntries(String title, List<SelectionListScreen.Entry> entries, Consumer<String> selected) {
        minecraft.setScreen(new SelectionListScreen(this, title, entries, id -> { selected.accept(id); minecraft.setScreen(this); }));
    }

    private void deleteSelectedConditions() { conditions.removeIf(c -> c.selected && conditions.size() > 1); if (conditions.isEmpty()) addCondition(); }
    private int selectedConditionCount() { int count = 0; for (ConditionDraft c : conditions) if (c.selected) count++; return count; }

    private TriggerType type() { return conditions.isEmpty() ? TriggerType.parse(rule.triggerType) : conditions.get(0).type; }
    private boolean counted() { return counted(type()); }
    private boolean counted(TriggerType type) { return type == TriggerType.ITEM_ACQUIRED || type == TriggerType.ENTITY_KILLED || type == TriggerType.OBSERVE_BLOCK || type == TriggerType.OBSERVE_ENTITY || type == TriggerType.HOVER_ITEM; }
    private boolean supportsNbt() { return supportsNbt(type()); }
    private boolean supportsNbt(TriggerType type) { return type == TriggerType.ITEM_ACQUIRED || type == TriggerType.HOVER_ITEM; }
    private void cycleDisplay() { read(); rule.displayStyle = "SIDE".equalsIgnoreCase(rule.displayStyle) ? "HORIZONTAL" : "SIDE"; rebuildWidgets(); }
    private Component displayName() { return Component.translatable("SIDE".equalsIgnoreCase(rule.displayStyle) ? "item_get.display.side" : "item_get.display.full"); }
    private void cycle(int d) { TriggerType[] v = TriggerType.values(); setType(v[Math.floorMod(type().ordinal() + d, v.length)]); }
    private void setType(TriggerType t) { if (conditions.isEmpty()) addCondition(); conditions.get(0).type = t; applyDefaults(t, conditions.get(0).data = new JsonObject()); writeConditions(); rebuildWidgets(); }
    private void defaults(TriggerType t) { switch(t) { case ITEM_ACQUIRED -> put("item", "minecraft:diamond"); case ENTITY_KILLED -> put("entity", "minecraft:zombie"); case HEALTH_AT -> put("value", 10); case HUNGER_AT -> put("value", 10); case EFFECT_GAINED -> put("effect", "minecraft:speed"); case WEATHER_IS -> put("weather", "rain"); case TIME_IS -> put("time", "day"); case ENTER_BIOME -> put("biome", "minecraft:plains"); case ENTER_STRUCTURE -> put("structure", "minecraft:village_plains"); case DEATH_BY -> put("death", "minecraft:fall"); case ADVANCEMENT_DONE -> put("advancement", "minecraft:story/mine_diamond"); case OBSERVE_BLOCK -> { put("block", "minecraft:oak_log"); put("count", 40); } case OBSERVE_ENTITY -> { put("entity", "minecraft:zombie"); put("count", 40); } case HOVER_ITEM -> { put("item", "minecraft:diamond"); put("count", 40); } } }
    private void applyDefaults(TriggerType t, JsonObject data) { switch(t) { case ITEM_ACQUIRED -> data.addProperty("item", "minecraft:diamond"); case ENTITY_KILLED -> data.addProperty("entity", "minecraft:zombie"); case HEALTH_AT -> data.addProperty("value", 10); case HUNGER_AT -> data.addProperty("value", 10); case EFFECT_GAINED -> data.addProperty("effect", "minecraft:speed"); case WEATHER_IS -> data.addProperty("weather", "rain"); case TIME_IS -> data.addProperty("time", "day"); case ENTER_BIOME -> data.addProperty("biome", "minecraft:plains"); case ENTER_STRUCTURE -> data.addProperty("structure", "minecraft:village_plains"); case DEATH_BY -> data.addProperty("death", "minecraft:fall"); case ADVANCEMENT_DONE -> data.addProperty("advancement", "minecraft:story/mine_diamond"); case OBSERVE_BLOCK -> { data.addProperty("block", "minecraft:oak_log"); data.addProperty("count", 40); } case OBSERVE_ENTITY -> { data.addProperty("entity", "minecraft:zombie"); data.addProperty("count", 40); } case HOVER_ITEM -> { data.addProperty("item", "minecraft:diamond"); data.addProperty("count", 40); } } }
    private void put(String k, String v) { if (!rule.trigger.has(k)) rule.trigger.addProperty(k, v); }
    private void put(String k, double v) { if (!rule.trigger.has(k)) rule.trigger.addProperty(k, v); }
    private void openTypeList() { read(); List<SelectionListScreen.Entry> e = new ArrayList<>(); for (TriggerType t : TriggerType.values()) e.add(new SelectionListScreen.Entry(t.name(), Component.translatable(t.translationKey).getString())); minecraft.setScreen(new SelectionListScreen(this, tr("item_get.editor.choose_trigger"), e, id -> setType(TriggerType.valueOf(id)))); }

    private String inputHint(TriggerType type) {
        return switch (type) {
            case ITEM_ACQUIRED, HOVER_ITEM -> "item_get.editor.input.item_nbt";
            case ENTITY_KILLED, OBSERVE_ENTITY -> "item_get.editor.input.entity";
            case HEALTH_AT, HUNGER_AT -> "item_get.editor.input.number";
            case EFFECT_GAINED -> "item_get.editor.input.effect";
            case WEATHER_IS -> "item_get.editor.input.weather";
            case TIME_IS -> "item_get.editor.input.time";
            case ENTER_BIOME -> "item_get.editor.input.biome";
            case ENTER_STRUCTURE -> "item_get.editor.input.structure";
            case DEATH_BY -> "item_get.editor.input.death";
            case ADVANCEMENT_DONE -> "item_get.editor.input.advancement";
            case OBSERVE_BLOCK -> "item_get.editor.input.block";
        };
    }

    private String conditionInput(ConditionDraft c) {
        String key = conditionKey(c.type);
        String value = dataValue(c.data, key, defaultValue(c.type));
        if (supportsNbt(c.type) && c.data.has("nbt") && !c.data.get("nbt").getAsString().isBlank()) return value + c.data.get("nbt").getAsString();
        return value;
    }

    private void applyConditionInput(ConditionDraft c, String value) {
        if (value.isBlank()) return;
        String key = conditionKey(c.type);
        if (supportsNbt(c.type)) {
            int nbt = value.indexOf('{');
            if (nbt >= 0) {
                c.data.addProperty(key, value.substring(0, nbt).trim());
                c.data.addProperty("nbt", value.substring(nbt).trim());
            } else {
                c.data.addProperty(key, value);
                c.data.remove("nbt");
            }
        } else if (c.type == TriggerType.HEALTH_AT || c.type == TriggerType.HUNGER_AT) {
            try { c.data.addProperty(key, Double.parseDouble(value)); } catch (Exception ignored) {}
        } else {
            c.data.addProperty(key, value);
        }
    }

    private String conditionKey(TriggerType type) {
        return switch(type) { case ITEM_ACQUIRED, HOVER_ITEM -> "item"; case ENTITY_KILLED, OBSERVE_ENTITY -> "entity"; case HEALTH_AT, HUNGER_AT -> "value"; case EFFECT_GAINED -> "effect"; case WEATHER_IS -> "weather"; case TIME_IS -> "time"; case ENTER_BIOME -> "biome"; case ENTER_STRUCTURE -> "structure"; case DEATH_BY -> "death"; case ADVANCEMENT_DONE -> "advancement"; case OBSERVE_BLOCK -> "block"; };
    }
    private String defaultValue(TriggerType type) { JsonObject data = new JsonObject(); applyDefaults(type, data); return dataValue(data, conditionKey(type), ""); }
    private String dataValue(JsonObject data, String k, String f) { return data.has(k) ? data.get(k).getAsString() : f; }
    private int threshold(JsonObject data) { try { return Math.max(1, data.has("count") ? data.get("count").getAsInt() : 1); } catch (Exception ignored) { return 1; } }

    private String targetLabel() { return switch(type()) {
        case ITEM_ACQUIRED -> tr("item_get.editor.item", ManagerScreen.item(rule.target()).getHoverName().getString());
        case ENTITY_KILLED -> tr("item_get.editor.entity", entityName(value("entity", "minecraft:zombie")));
        case HEALTH_AT -> tr("item_get.editor.health", value("value", "10"));
        case HUNGER_AT -> tr("item_get.editor.hunger", value("value", "10"));
        case EFFECT_GAINED -> tr("item_get.editor.effect", effectName(value("effect", "minecraft:speed")));
        case WEATHER_IS -> tr("item_get.editor.weather", choiceName(value("weather", "rain")));
        case TIME_IS -> tr("item_get.editor.time", choiceName(value("time", "day")));
        case ENTER_BIOME -> tr("item_get.editor.biome", translated("biome", value("biome", "minecraft:plains")));
        case ENTER_STRUCTURE -> tr("item_get.editor.structure", translated("structure", value("structure", "minecraft:village_plains")));
        case DEATH_BY -> tr("item_get.editor.death", ClientHooks.damageTypeName(value("death", "minecraft:fall")));
        case ADVANCEMENT_DONE -> tr("item_get.editor.advancement", ClientHooks.advancementName(value("advancement", "minecraft:story/mine_diamond")));
        case OBSERVE_BLOCK -> tr("item_get.editor.block", ManagerScreen.item(value("block", "minecraft:oak_log")).getHoverName().getString());
        case OBSERVE_ENTITY -> tr("item_get.editor.entity", entityName(value("entity", "minecraft:zombie")));
        case HOVER_ITEM -> tr("item_get.editor.item", ManagerScreen.item(value("item", "minecraft:diamond")).getHoverName().getString());
    }; }

    private void openTarget() { read(); switch(type()) {
        case ITEM_ACQUIRED -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { setItemTarget("item", stack, true); minecraft.setScreen(this); }));
        case ENTITY_KILLED -> minecraft.setScreen(new EntityPickerScreen(this, id -> { rule.trigger.addProperty("entity", id); minecraft.setScreen(this); }));
        case HEALTH_AT, HUNGER_AT -> minecraft.setScreen(new NumberInputScreen(this, tr(type() == TriggerType.HEALTH_AT ? "item_get.editor.health_threshold" : "item_get.editor.hunger_threshold"), value("value", "10"), v -> { try { rule.trigger.addProperty("value", Double.parseDouble(v)); } catch (Exception ignored) {} minecraft.setScreen(this); }));
        case EFFECT_GAINED -> openEntries(tr("item_get.editor.choose_effect"), effects(), "effect");
        case WEATHER_IS -> openEntries(tr("item_get.editor.choose_weather"), List.of(entry("clear", choiceName("clear")), entry("rain", choiceName("rain")), entry("thunder", choiceName("thunder"))), "weather");
        case TIME_IS -> openEntries(tr("item_get.editor.choose_time"), List.of(entry("day", choiceName("day")), entry("noon", choiceName("noon")), entry("night", choiceName("night")), entry("midnight", choiceName("midnight"))), "time");
        case ENTER_BIOME -> openEntries(tr("item_get.editor.choose_biome"), catalog(ClientHooks.biomes(), "biome"), "biome");
        case ENTER_STRUCTURE -> openEntries(tr("item_get.editor.choose_structure"), catalog(ClientHooks.structures(), "structure"), "structure");
        case DEATH_BY -> openEntries(tr("item_get.editor.choose_death"), namedCatalog(ClientHooks.damageTypes(), ClientHooks::damageTypeName), "death");
        case ADVANCEMENT_DONE -> openEntries(tr("item_get.editor.choose_advancement"), namedCatalog(ClientHooks.advancements(), ClientHooks::advancementName), "advancement");
        case OBSERVE_BLOCK -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { rule.trigger.addProperty("block", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); minecraft.setScreen(this); }));
        case OBSERVE_ENTITY -> minecraft.setScreen(new EntityPickerScreen(this, id -> { rule.trigger.addProperty("entity", id); minecraft.setScreen(this); }));
        case HOVER_ITEM -> minecraft.setScreen(new ItemPickerScreen(this, stack -> { setItemTarget("item", stack, true); minecraft.setScreen(this); }));
    } }
    private void openEntries(String title, List<SelectionListScreen.Entry> entries, String key) { minecraft.setScreen(new SelectionListScreen(this, title, entries, id -> { rule.trigger.addProperty(key, id); minecraft.setScreen(this); })); }
    private List<SelectionListScreen.Entry> effects() { List<SelectionListScreen.Entry> out = new ArrayList<>(); BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e -> { String id = e.getKey().location().toString(); out.add(entry(id, e.getValue().getDisplayName().getString())); }); out.sort(Comparator.comparing(SelectionListScreen.Entry::name)); return out; }
    private List<SelectionListScreen.Entry> catalog(List<String> ids, String prefix) { List<SelectionListScreen.Entry> out = new ArrayList<>(); ids.forEach(id -> out.add(entry(id, translated(prefix, id)))); out.sort(Comparator.comparing(SelectionListScreen.Entry::name)); return out; }
    private List<SelectionListScreen.Entry> namedCatalog(List<String> ids, java.util.function.Function<String, String> names) { List<SelectionListScreen.Entry> out = new ArrayList<>(); ids.forEach(id -> out.add(entry(id, names.apply(id)))); out.sort(Comparator.comparing(SelectionListScreen.Entry::name)); return out; }
    private void openConditionPicker() { read(); List<SelectionListScreen.Entry> e = new ArrayList<>(); for (TriggerType t : TriggerType.values()) e.add(new SelectionListScreen.Entry(t.name(), Component.translatable(t.translationKey).getString())); minecraft.setScreen(new SelectionListScreen(this, tr("item_get.editor.choose_condition"), e, id -> { rule.trigger.addProperty("condition_preview", id); minecraft.setScreen(this); })); }
    private void openIconPicker() { read(); minecraft.setScreen(new IconSourcePickerScreen(this, stack -> { rule.icon = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); rule.iconImage = ""; rule.iconStack = stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString(); minecraft.setScreen(this); }, image -> { rule.icon = ""; rule.iconImage = image; rule.iconStack = ""; minecraft.setScreen(this); })); }
    private void openPonderBinding() { read(); if (ClientHooks.openPonderSelector(this, id -> { rule.ponderTarget = id; minecraft.setScreen(this); }, () -> minecraft.setScreen(this))) return; minecraft.setScreen(new ItemPickerScreen(this, stack -> { rule.ponderTarget = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); minecraft.setScreen(this); })); }
    private void preview() { read(); if ("SIDE".equalsIgnoreCase(rule.displayStyle)) SideReminderOverlay.preview(rule); else minecraft.setScreen(new ReminderScreen(rule, this)); }
    private void read() {
        if (count != null) {
            try { rule.trigger.addProperty("count", Math.max(1, Integer.parseInt(count.getValue()))); }
            catch (Exception ignored) { rule.trigger.addProperty("count", 1); }
        }
        if (nbtBox != null) {
            String nbt = nbtBox.getValue().trim();
            if (nbt.isBlank()) rule.trigger.remove("nbt");
            else rule.trigger.addProperty("nbt", nbt);
        }
        if (titleBox != null) rule.title = titleBox.getValue();
        if (subtitleBox != null) rule.subtitle = subtitleBox.getValue();
        if (!conditions.isEmpty()) writeConditions();
    }
    private void applyDraft() { writeConditions(); original.id = rule.id; original.triggerType = rule.triggerType; original.trigger = rule.trigger.deepCopy(); original.title = rule.title; original.subtitle = rule.subtitle; original.description = rule.description; original.icon = rule.icon; original.iconImage = rule.iconImage; original.iconStack = rule.iconStack; original.ponderTarget = rule.ponderTarget; original.displayStyle = rule.displayStyle; original.sound = rule.sound; original.music = rule.music; original.pauseSingleplayer = rule.pauseSingleplayer; original.enabled = rule.enabled; }
    private String iconName() { return ConfigIconLibrary.label(rule); }
    private String ponderName() { if (rule.ponderTarget == null || rule.ponderTarget.isBlank()) return tr("item_get.editor.ponder_none"); ItemStack stack = ManagerScreen.item(rule.ponderTarget); return stack.isEmpty() ? rule.ponderTarget : stack.getHoverName().getString(); }
    private String targetNameForNbt() { return targetStackForNbtButton().getHoverName().getString(); }
    private void setItemTarget(String key, ItemStack stack, boolean fillNbt) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) rule.trigger.addProperty(key, id.toString());
        rule.trigger.addProperty("stack", stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString());
        if (!fillNbt) return;
        String nbt = stackNbtForInput(stack);
        if (!nbt.isBlank()) rule.trigger.addProperty("nbt", nbt);
    }
    private void setItemTarget(JsonObject data, String key, ItemStack stack, boolean fillNbt) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) data.addProperty(key, id.toString());
        data.addProperty("stack", stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString());
        if (!fillNbt) return;
        String nbt = stackNbtForInput(stack);
        if (nbt.isBlank()) data.remove("nbt"); else data.addProperty("nbt", nbt);
    }
    private String stackNbtForInput(ItemStack stack) { return stack.save(minecraft.level.registryAccess()).toString(); }
    private ItemStack targetStackForNbtButton() {
        if (rule.trigger.has("stack")) try {
            ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(rule.trigger.get("stack").getAsString()));
            if (!stack.isEmpty()) return stack;
        } catch (Exception ignored) {}
        return ManagerScreen.item(rule.target());
    }
    private String value(String k, String f) { return rule.trigger.has(k) ? rule.trigger.get(k).getAsString() : f; }
    private static SelectionListScreen.Entry entry(String id, String name) { return new SelectionListScreen.Entry(id, name); }
    private String entityName(String id) { var t = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(id)); return t == null ? id : t.getDescription().getString(); }
    private String effectName(String id) { var e = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(id)); return e == null ? id : e.getDisplayName().getString(); }
    private String translated(String prefix, String id) { if (prefix == null || prefix.isBlank()) return id; ResourceLocation r = ResourceLocation.tryParse(id); if (r == null) return id; String key = prefix + "." + r.getNamespace() + "." + r.getPath().replace('/', '.'); return I18n.exists(key) ? I18n.get(key) : id; }
    private String choiceName(String id) { return tr("item_get.choice." + id); }
    private static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        read();
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        drawShell(g);
        drawNav(g, mx, my);
        drawSection(g);
        drawPreview(g);
        super.render(g, mx, my, partial);
        drawNbtItemButton(g, mx, my);
        drawConditionIcons(g, mx, my);
    }

    private void drawShell(GuiGraphics g) {
        drawPanel(g, navX - 6, top - 14, navX + navW + 6, bottom + 4);
        drawPanel(g, formX - 8, top - 14, formX + formW + 8, bottom + 4);
        drawPanel(g, previewX - 8, top - 14, previewX + previewW + 8, bottom + 4);
    }

    private void drawNav(GuiGraphics g, int mx, int my) {
        g.drawString(font, Component.translatable("item_get.editor.nav"), navX, top - 8, 0xFFE9B0);
        navScroll = Math.max(0, Math.min(maxNavScroll(), navScroll));
        g.enableScissor(navX - 2, navTop(), navX + navW + 2, navBottom());
        Section[] values = Section.values();
        for (int i = 0; i < values.length; i++) {
            int y = navTop() + i * row - navScroll;
            if (y + row - 3 < navTop() || y > navBottom()) continue;
            boolean active = values[i] == section, hover = mx >= navX && mx <= navX + navW && my >= y && my <= y + row - 4;
            g.fill(navX, y, navX + navW, y + row - 4, active ? 0xAA7D6846 : hover ? 0x66513A28 : 0x4433261A);
            if (active) g.fill(navX, y, navX + 2, y + row - 4, 0xFFE9B0);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(values[i].key).getString(), navW - 8), navX + 5, y + 6, active ? 0xFFE9B0 : 0xD8D8D8, false);
        }
        g.disableScissor();
        if (maxNavScroll() > 0) {
            int track = navBottom() - navTop();
            int thumb = Math.max(16, track * track / (track + maxNavScroll()));
            int thumbY = navTop() + (track - thumb) * navScroll / maxNavScroll();
            g.fill(navX + navW + 3, navTop(), navX + navW + 4, navBottom(), 0x447D6846);
            g.fill(navX + navW + 2, thumbY, navX + navW + 5, thumbY + thumb, 0xAA7D6846);
        }
    }

    private void drawSection(GuiGraphics g) {
        g.drawString(font, Component.translatable(section.key), formX, top - 8, 0xFFE9B0);
        int h = height < 300 ? 18 : 20;
        int y = top + 18;
        switch (section) {
            case BASIC -> {
                g.drawString(font, Component.translatable("item_get.editor.title"), formX, y, 0xD8D8D8);
                g.drawString(font, Component.translatable("item_get.editor.subtitle"), formX, y + h + 18, 0xD8D8D8);
                g.drawString(font, Component.translatable("item_get.editor.description"), formX, y + h * 2 + 36, 0xD8D8D8);
                drawDescriptionBox(g);
            }
            case TRIGGER -> {
                String logicText = conditions.size() <= 1 ? "" : "  " + tr("AND".equals(conditionLogic) ? "item_get.editor.logic_all" : "item_get.editor.logic_any");
                g.drawString(font, Component.translatable("item_get.editor.section.trigger").getString() + logicText, formX, y, 0xD8D8D8);
                int cy = top + 30;
                for (ConditionDraft c : conditions) {
                    if (c.input == null) continue;
                    if (c.count != null) g.drawString(font, Component.translatable("item_get.editor.count"), formX, c.count.getY() + 6, 0xAFC4D8);
                    cy += counted(c.type) ? h * 2 + 7 : h + 8;
                }
            }
            case DISPLAY -> g.drawString(font, Component.translatable("item_get.editor.section.look"), formX, y, 0xD8D8D8);
            case AUDIO -> g.drawString(font, Component.translatable("item_get.editor.sound_current", soundName()), formX, y, 0xAFC4D8);
            case PONDER -> g.drawString(font, Component.translatable("item_get.editor.ponder_current", ponderName()), formX, y, 0xAFC4D8);
            case DEBUG -> g.drawString(font, ManagerScreen.displaySubtitle(rule, Util.getMillis()), formX, y, 0xAFC4D8);
        }
    }

    private void drawPreview(GuiGraphics g) {
        g.drawString(font, Component.translatable("item_get.editor.preview_panel"), previewX, top - 8, 0xFFE9B0);
        if ("SIDE".equalsIgnoreCase(rule.displayStyle)) drawSidePreview(g);
        else drawFullPreview(g);
    }

    private void drawNbtItemButton(GuiGraphics g, int mx, int my) {
        if (nbtBox == null || nbtButtonSize <= 0) return;
        ItemStack stack = targetStackForNbtButton();
        g.renderItem(stack, nbtButtonX + (nbtButtonSize - 16) / 2, nbtButtonY + (nbtButtonSize - 16) / 2);
        if (mx >= nbtButtonX && mx < nbtButtonX + nbtButtonSize && my >= nbtButtonY && my < nbtButtonY + nbtButtonSize) {
            g.renderTooltip(font, List.of(Component.translatable("item_get.editor.choose_item"), stack.getHoverName()), java.util.Optional.empty(), mx, my);
        }
    }

    private void drawConditionIcons(GuiGraphics g, int mx, int my) {
        for (ConditionDraft c : conditions) {
            if (c.input == null) continue;
            int x = c.input.getX() + c.input.getWidth() + 3, y = c.input.getY() - 2;
            ItemStack stack = conditionIcon(c);
            if (!stack.isEmpty()) g.renderItem(stack, x + 2, y + 2);
            if (mx >= x && mx <= x + 20 && my >= y && my <= y + 20) g.renderTooltip(font, Component.translatable("item_get.editor.choose_item"), mx, my);
        }
    }

    private ItemStack conditionIcon(ConditionDraft c) {
        return switch (c.type) {
            case ITEM_ACQUIRED, HOVER_ITEM -> ManagerScreen.item(dataValue(c.data, "item", "minecraft:diamond"));
            case OBSERVE_BLOCK -> ManagerScreen.item(dataValue(c.data, "block", "minecraft:oak_log"));
            case ENTITY_KILLED, OBSERVE_ENTITY -> new ItemStack(net.minecraft.world.item.Items.SPYGLASS);
            case HEALTH_AT -> new ItemStack(net.minecraft.world.item.Items.GLISTERING_MELON_SLICE);
            case HUNGER_AT -> new ItemStack(net.minecraft.world.item.Items.BREAD);
            case EFFECT_GAINED -> new ItemStack(net.minecraft.world.item.Items.POTION);
            case WEATHER_IS -> new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
            case TIME_IS -> new ItemStack(net.minecraft.world.item.Items.CLOCK);
            case ENTER_BIOME -> new ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK);
            case ENTER_STRUCTURE -> new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
            case DEATH_BY -> new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL);
            case ADVANCEMENT_DONE -> new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK);
        };
    }

    private void drawSidePreview(GuiGraphics g) {
        int panelW = Math.min(180, previewW - 18), panelH = 32;
        int right = previewX + previewW - 6, left = right - panelW, y = top + 34;
        g.fillGradient(left, y, right, y + panelH, 0, 0xAA000000, 0x76000000);
        g.fill(left, y, left + 2, y + panelH, 0xD0D8E7F5);
        boolean image = ConfigIconLibrary.render(g, rule, left + 17, y + 16, .85F);
        if (!image) {
            ItemStack stack = ManagerScreen.displayStack(rule);
            g.pose().pushPose(); g.pose().translate(left + 17, y + 16, 0); g.pose().scale(.85F, .85F, 1); g.renderItem(stack, -8, -8); g.pose().popPose();
        }
        int textX = left + 33, textW = Math.max(44, right - textX - 6);
        String heading = TranslatedText.resolve(rule.title); if (heading.isBlank()) heading = tr("item_get.manager.unnamed");
        g.drawString(font, font.plainSubstrByWidth(heading, textW), textX, y + 6, 0xFFE9B0);
        g.drawString(font, font.split(ManagerScreen.displaySubtitle(rule, Util.getMillis()), textW).get(0), textX, y + 18, 0xDDE6EE);
    }

    private void drawFullPreview(GuiGraphics g) {
        int cx = previewX + previewW / 2, cy = top + 120;
        int w = Math.max(105, Math.min(previewW - 10, 260)), h = previewW < 180 ? 82 : 104;
        int left = cx - w / 2, topY = cy - h / 2;
        g.fillGradient(left, topY, left + w, topY + h, 0, 0xBE000000, 0x94000000);
        float scale = previewW < 180 ? 1.35F : 1.85F;
        int itemX = previewW < 180 ? left + 26 : left + 42;
        boolean image = ConfigIconLibrary.render(g, rule, itemX, cy - 2, scale);
        if (!image) {
            ItemStack stack = ManagerScreen.displayStack(rule);
            g.pose().pushPose(); g.pose().translate(itemX, cy - 2, 0); g.pose().scale(scale, scale, 1); g.renderItem(stack, -8, -8); g.pose().popPose();
        }
        int textX = previewW < 180 ? left + 50 : left + 78, textW = Math.max(48, w - (previewW < 180 ? 58 : 88));
        String heading = TranslatedText.resolve(rule.title); if (heading.isBlank()) heading = tr("item_get.manager.unnamed");
        g.drawCenteredString(font, font.plainSubstrByWidth(heading, textW), textX + textW / 2, topY + 16, 0xFFE9B0);
        g.drawCenteredString(font, ManagerScreen.displaySubtitle(rule, Util.getMillis()), textX + textW / 2, topY + 32, 0xFFFFFF);
        drawPreviewDescription(g, textX, topY + 50, textW, topY + h - 8);
    }

    private void drawPreviewDescription(GuiGraphics g, int x, int y, int w, int bottomY) {
        String desc = TranslatedText.resolve(rule.description);
        if (desc.isBlank()) return;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(desc), w - 8);
        int visible = Math.max(1, (bottomY - y) / 11);
        int max = Math.max(0, lines.size() - visible);
        previewScroll = Math.max(0, Math.min(max, previewScroll));
        g.enableScissor(x, y, x + w, bottomY);
        int count = Math.min(visible, Math.max(0, lines.size() - previewScroll));
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(previewScroll + i), x + 2, y + i * 11, 0xD0D7DF, false);
        g.disableScissor();
        if (max > 0) {
            int track = bottomY - y, thumb = Math.max(10, track * visible / lines.size()), thumbY = y + (track - thumb) * previewScroll / max;
            g.fill(x + w - 3, y, x + w - 2, bottomY, 0x55FFFFFF);
            g.fill(x + w - 4, thumbY, x + w - 1, thumbY + thumb, 0xAAFFFFFF);
        }
    }

    private int navTop() { return top + 12; }
    private int navBottom() { return bottom - 4; }
    private int navAt(double x, double y) {
        if (x < navX || x > navX + navW || y < navTop() || y > navBottom()) return -1;
        int index = ((int)y - navTop() + navScroll) / row;
        return index >= 0 && index < Section.values().length ? index : -1;
    }
    private int maxNavScroll() { return Math.max(0, Section.values().length * row - (navBottom() - navTop())); }
    private String soundName() { return rule.sound == null || rule.sound.isBlank() ? "item_get:item_acquired" : rule.sound; }
    private int maxPreviewScroll() {
        int w = Math.max(105, Math.min(previewW - 10, 260)), h = previewW < 180 ? 82 : 104;
        int textW = Math.max(48, w - (previewW < 180 ? 58 : 88));
        String desc = TranslatedText.resolve(rule.description);
        if (desc.isBlank()) return 0;
        int visible = Math.max(1, (h - 58) / 11);
        return Math.max(0, font.split(Component.literal(desc), textW - 8).size() - visible);
    }

    private int descX() { return formX; }
    private int descY() { return top + 116; }
    private int descW() { return formW; }
    private int descH() { return Math.max(44, Math.min(78, bottom - descY() - 36)); }
    private boolean descHit(double x, double y) { return x >= descX() && x <= descX() + descW() && y >= descY() && y <= descY() + descH(); }
    private List<net.minecraft.util.FormattedCharSequence> descLines() {
        String text = rule.description == null || rule.description.isBlank() ? tr("item_get.editor.description_empty") : TranslatedText.resolve(rule.description);
        return font.split(Component.literal(text), descW() - 8);
    }
    private int descVisibleLines() { return Math.max(1, (descH() - 6) / 11); }
    private int maxDescScroll() { return Math.max(0, descLines().size() - descVisibleLines()); }
    private void drawDescriptionBox(GuiGraphics g) {
        int x = descX(), y = descY(), w = descW(), h = descH();
        g.fill(x, y, x + w, y + h, descFocused ? 0xAA050505 : 0x88050505);
        g.fill(x, y, x + w, y + 1, descFocused ? 0xFFE0E0FF : 0xAA9A9A9A);
        g.fill(x, y + h - 1, x + w, y + h, descFocused ? 0xFFE0E0FF : 0xAA9A9A9A);
        g.fill(x, y, x + 1, y + h, descFocused ? 0xFFE0E0FF : 0xAA9A9A9A);
        g.fill(x + w - 1, y, x + w, y + h, descFocused ? 0xFFE0E0FF : 0xAA9A9A9A);
        List<net.minecraft.util.FormattedCharSequence> lines = descLines();
        descScroll = Math.max(0, Math.min(maxDescScroll(), descScroll));
        int count = Math.min(descVisibleLines(), Math.max(0, lines.size() - descScroll));
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(descScroll + i), x + 4, y + 4 + i * 11, rule.description == null || rule.description.isBlank() ? 0x7F8FA0 : 0xD0D7DF, false);
        if (maxDescScroll() > 0) {
            int track = h - 6, thumb = Math.max(10, track * descVisibleLines() / lines.size()), thumbY = y + 3 + (track - thumb) * descScroll / maxDescScroll();
            g.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, 0x55FFFFFF);
            g.fill(x + w - 5, thumbY, x + w - 2, thumbY + thumb, 0xAAFFFFFF);
        }
    }

    private void drawPanel(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left, top, right, bottom, 0x66000000);
        g.fill(left, top, right, top + 1, 0x44FFFFFF);
        g.fill(left, bottom - 1, right, bottom, 0x33000000);
    }

    private enum Section {
        BASIC("item_get.editor.tab.basic"),
        TRIGGER("item_get.editor.tab.trigger"),
        DISPLAY("item_get.editor.tab.display"),
        AUDIO("item_get.editor.tab.audio"),
        PONDER("item_get.editor.tab.ponder"),
        DEBUG("item_get.editor.tab.debug");

        private final String key;
        Section(String key) { this.key = key; }
    }

    private static final class ConditionDraft {
        private TriggerType type;
        private JsonObject data;
        private boolean selected;
        private EditBox input;
        private EditBox count;

        private ConditionDraft(TriggerType type, JsonObject data) {
            this.type = type;
            this.data = data == null ? new JsonObject() : data;
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
