package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.network.SaveRulesPacket;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.TagParser;
import com.kuzhi.itemget.rule.TriggerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.language.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import net.minecraft.Util;

public final class ManagerScreen extends CrispScreen {
    private static final int LIST_TOP=34;
    private final List<ReminderRule> rules;
    private final boolean editable;
    private int selected = -1;
    private int page;
    private boolean multiSelect;
    private final Set<Integer> selectedMany = new HashSet<>();
    private int pressedIndex=-1;private long pressedAt;private boolean dragSelecting;

    public ManagerScreen(List<ReminderRule> rules, boolean editable) {
        super(Component.translatable("item_get.manager.title"));
        this.rules = new ArrayList<>(rules); this.editable = editable;
    }

    @Override protected void init() {
        int pageY = height - 49;
        Button duplicate = Button.builder(Component.translatable("item_get.manager.duplicate"), b -> duplicateSelected()).bounds(width / 2 - 205, pageY, 68, 18).build(); duplicate.active = editable; addRenderableWidget(duplicate);
        Button previous = Button.builder(Component.translatable("item_get.manager.previous"), b -> { page--; selected = -1; rebuildWidgets(); }).bounds(width / 2 - 120, pageY, 72, 18).build(); previous.active = page > 0; addRenderableWidget(previous);
        Button indicator = Button.builder(Component.translatable("item_get.manager.page",page+1,pageCount()), b -> {}).bounds(width / 2 - 44, pageY, 88, 18).build(); indicator.active = false; addRenderableWidget(indicator);
        Button next = Button.builder(Component.translatable("item_get.manager.next"), b -> { page++; selected = -1; rebuildWidgets(); }).bounds(width / 2 + 48, pageY, 72, 18).build(); next.active = page + 1 < pageCount(); addRenderableWidget(next);
        int y = height - 27;
        if (multiSelect) {
            Button enable = Button.builder(Component.translatable("item_get.manager.enable_selected"), b -> setSelectedEnabled(true)).bounds(width / 2 - 205, y, 98, 20).build(); enable.active = editable; addRenderableWidget(enable);
            Button disable = Button.builder(Component.translatable("item_get.manager.disable_selected"), b -> setSelectedEnabled(false)).bounds(width / 2 - 103, y, 98, 20).build(); disable.active = editable; addRenderableWidget(disable);
            Button delete = Button.builder(Component.translatable("item_get.manager.delete_selected"), b -> deleteSelected()).bounds(width / 2 + 1, y, 98, 20).build(); delete.active = editable; addRenderableWidget(delete);
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> clearMultiSelection()).bounds(width / 2 + 103, y, 102, 20).build());
            return;
        }
        Button create = Button.builder(Component.translatable("item_get.manager.create"), b -> edit(new ReminderRule())).bounds(width / 2 - 205, y, 78, 20).build(); create.active = editable; addRenderableWidget(create);
        Button edit = Button.builder(Component.translatable("item_get.manager.edit"), b -> { if (selected >= 0) edit(rules.get(selected)); }).bounds(width / 2 - 123, y, 58, 20).build(); edit.active = editable; addRenderableWidget(edit);
        addRenderableWidget(Button.builder(Component.translatable("item_get.manager.test"), b -> { if (selected >= 0) minecraft.setScreen(new ReminderScreen(rules.get(selected), this)); }).bounds(width / 2 - 61, y, 58, 20).build());
        Button toggle = Button.builder(Component.translatable("item_get.manager.toggle"), b -> toggleEnabled()).bounds(width / 2 + 1, y, 78, 20).build(); toggle.active = editable; addRenderableWidget(toggle);
        Button delete = Button.builder(Component.translatable("item_get.manager.delete"), b -> deleteSelected()).bounds(width / 2 + 83, y, 58, 20).build(); delete.active = editable; addRenderableWidget(delete);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 145, y, 60, 20).build());
    }

    private void edit(ReminderRule rule) { minecraft.setScreen(new RuleEditorScreen(this, rule, rules.contains(rule), saved -> { if (!rules.contains(saved)) rules.add(saved); save(); })); }
    private void save() { ItemGetNetwork.saveRules(new SaveRulesPacket(com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(rules)).toString())); }
    private void duplicateSelected(){if(selected<0||selected>=rules.size())return;ReminderRule copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(rules.get(selected)),ReminderRule.class);copy.id=UUID.randomUUID().toString();copy.title=copy.title==null||copy.title.isBlank()?Component.translatable("item_get.manager.unnamed_copy").getString():Component.translatable("item_get.manager.copy_title",copy.title).getString();int index=selected+1;rules.add(index,copy);selected=index;page=index/pageSize();save();rebuildWidgets();}
    private void toggleEnabled(){if(multiSelect){for(int i:selectedMany)if(i>=0&&i<rules.size())rules.get(i).enabled=true;selectedMany.clear();multiSelect=false;}else if(selected>=0&&selected<rules.size())rules.get(selected).enabled=!rules.get(selected).enabled;save();rebuildWidgets();}
    private void setSelectedEnabled(boolean enabled){for(int i:selectedMany)if(i>=0&&i<rules.size())rules.get(i).enabled=enabled;save();clearMultiSelection();}
    private void clearMultiSelection(){selectedMany.clear();multiSelect=false;selected=-1;rebuildWidgets();}
    private void deleteSelected(){if(multiSelect){selectedMany.stream().sorted(Comparator.reverseOrder()).forEach(i->{if(i>=0&&i<rules.size())rules.remove((int)i);});selectedMany.clear();multiSelect=false;}else if(selected>=0&&selected<rules.size())rules.remove(selected);selected=-1;page=Math.min(page,pageCount()-1);save();rebuildWidgets();}

    @Override public boolean mouseClicked(double x, double y, int button) {
        int index=rowAt(x,y);
        if(index>=0){pressedIndex=index;pressedAt=Util.getMillis();dragSelecting=false;return true;}
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&pressedIndex>=0&&Util.getMillis()-pressedAt>=280){if(!dragSelecting){dragSelecting=true;multiSelect=true;selectedMany.clear();selectedMany.add(pressedIndex);}int hovered=rowAt(x,y);if(hovered>=0)selectedMany.add(hovered);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(pressedIndex>=0){int index=pressedIndex;pressedIndex=-1;if(dragSelecting){dragSelecting=false;selected=-1;}else if(multiSelect){multiSelect=false;selectedMany.clear();selected=-1;}else selected=selected==index?-1:index;rebuildWidgets();return true;}return super.mouseReleased(x,y,button);}
    private int rowAt(double x,double y){int row=(int)((y-LIST_TOP)/34),index=page*pageSize()+row;return y>=LIST_TOP&&y<height-54&&x>=width/2-205&&x<=width/2+205&&row>=0&&row<pageSize()&&index<rules.size()?index:-1;}

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000); g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        if(multiSelect)g.drawString(font,Component.translatable("item_get.manager.selected_count",selectedMany.size()),width/2+132,height-44,0xFFE9B0);
        int y = LIST_TOP, start = page * pageSize(), end = Math.min(rules.size(), start + pageSize());
        if (rules.isEmpty()) g.drawCenteredString(font, Component.translatable("item_get.manager.empty"), width / 2, 80, 0x909090);
        for (int i = start; i < end; i++, y += 34) {
            ReminderRule rule = rules.get(i);
            boolean holding=i==pressedIndex&&Util.getMillis()-pressedAt>=250;g.fill(width / 2 - 205, y, width / 2 + 205, y + 30, i == selected || selectedMany.contains(i) || holding ? 0xAA405A72 : 0x88303030);
            ItemStack stack = displayStack(rule); g.renderItem(stack, width / 2 - 197, y + 7);
            String heading = rule.title == null || rule.title.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : rule.title;
            g.drawString(font, heading, width / 2 - 175, y + 5, rule.enabled ? 0xFFFFFF : 0x888888);
            g.drawString(font, triggerSummary(rule).getString() + (rule.enabled ? "" : Component.translatable("item_get.manager.disabled_suffix").getString()), width / 2 - 175, y + 17, 0xA0A0A0);
        }
        super.render(g, mx, my, partial);
    }

    private int pageSize() { return Math.max(1, (height - LIST_TOP - 56) / 34); }
    private int pageCount() { return Math.max(1, (rules.size() + pageSize() - 1) / pageSize()); }

    static ItemStack item(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
    static ItemStack displayStack(ReminderRule rule) {
        if (rule.iconStack != null && !rule.iconStack.isBlank()) try {
            ItemStack stack = ItemStack.parseOptional(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), TagParser.parseTag(rule.iconStack));
            if (!stack.isEmpty()) return stack;
        } catch (Exception ignored) {}
        if (rule.icon != null && !rule.icon.isBlank() && !item(rule.icon).isEmpty()) return item(rule.icon);
        return switch (TriggerType.parse(rule.triggerType)) { case ITEM_ACQUIRED -> item(rule.target()); case ENTITY_KILLED -> new ItemStack(Items.IRON_SWORD); case HEALTH_AT -> new ItemStack(Items.GLISTERING_MELON_SLICE); case HUNGER_AT -> new ItemStack(Items.BREAD); case EFFECT_GAINED -> new ItemStack(Items.POTION); case WEATHER_IS -> new ItemStack(Items.WATER_BUCKET); case TIME_IS -> new ItemStack(Items.CLOCK); case ENTER_BIOME -> new ItemStack(Items.GRASS_BLOCK); case ENTER_STRUCTURE -> new ItemStack(Items.FILLED_MAP); };
    }
    static Component targetName(ReminderRule rule) {
        TriggerType type = TriggerType.parse(rule.triggerType);
        if (type == TriggerType.ITEM_ACQUIRED) return item(rule.target()).getHoverName();
        if (type == TriggerType.ENTITY_KILLED) { var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(rule.entityTarget())); return entity == null ? Component.literal(rule.entityTarget()) : entity.getDescription(); }
        String key = switch(type){case HEALTH_AT,HUNGER_AT->"value";case EFFECT_GAINED->"effect";case WEATHER_IS->"weather";case TIME_IS->"time";case ENTER_BIOME->"biome";case ENTER_STRUCTURE->"structure";default->"";};
        String id = rule.trigger.has(key)?rule.trigger.get(key).getAsString():type.name();
        if(type==TriggerType.EFFECT_GAINED){var effect=BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(id));if(effect!=null)return effect.getDisplayName();}
        if(type==TriggerType.WEATHER_IS||type==TriggerType.TIME_IS)return Component.translatable("item_get.choice."+id);
        if(type==TriggerType.ENTER_BIOME||type==TriggerType.ENTER_STRUCTURE){ResourceLocation location=ResourceLocation.tryParse(id);if(location!=null){String prefix=type==TriggerType.ENTER_BIOME?"biome":"structure";String translation=prefix+"."+location.getNamespace()+"."+location.getPath().replace('/','.');if(I18n.exists(translation))return Component.literal(I18n.get(translation));}}
        return Component.literal(id);
    }
    static Component triggerSummary(ReminderRule rule) {
        TriggerType type = TriggerType.parse(rule.triggerType); String name = targetName(rule).getString();
        boolean counted = type == TriggerType.ITEM_ACQUIRED || type == TriggerType.ENTITY_KILLED;
        String key="item_get.summary."+type.name().toLowerCase(java.util.Locale.ROOT)+(counted && rule.threshold()==1?".one":"");
        return (type==TriggerType.ITEM_ACQUIRED||type==TriggerType.ENTITY_KILLED)?Component.translatable(key,name,rule.threshold()):Component.translatable(key,name,rule.trigger.has("value")?rule.trigger.get("value").getAsString():"");
    }
}
