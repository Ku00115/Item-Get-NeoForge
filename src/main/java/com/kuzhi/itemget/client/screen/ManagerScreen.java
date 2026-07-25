package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.ClientHooks;
import com.kuzhi.itemget.client.ConfigIconLibrary;
import com.kuzhi.itemget.client.SideReminderOverlay;
import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.network.SaveRulesPacket;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleConditions;
import com.kuzhi.itemget.rule.TriggerType;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ManagerScreen extends CrispScreen {
    private static final int ROW_H = 34;
    private final List<ReminderRule> rules;
    private final boolean editable;
    private final Set<Integer> selectedMany = new HashSet<>();
    private EditBox search;
    private String query = "";
    private ManagerFilter filter = ManagerFilter.ALL;
    private int selected = -1, scroll;
    private boolean multiSelect, dragSelecting, draggingScrollbar;
    private int pressedIndex = -1, scrollbarGrab;
    private long pressedAt;

    public ManagerScreen(List<ReminderRule> rules, boolean editable) {
        super(Component.translatable("item_get.manager.title"));
        this.rules = new ArrayList<>(rules);
        this.editable = editable;
    }

    @Override protected void init() {
        int top = controlsTop(), h = 18, left = listLeft();
        addRenderableWidget(Button.builder(Component.translatable(filter.key), b -> { cycleFilter(); }).bounds(left, top, 78, h).build());
        search = new EditBox(font, left + 84, top, Math.max(80, listRight() - left - 84), h, Component.translatable("item_get.handbook.search"));
        search.setValue(query);
        search.setHint(Component.translatable("item_get.handbook.search"));
        search.setResponder(v -> { query = v == null ? "" : v; scroll = 0; selected = -1; });
        addRenderableWidget(search);

        int y = height - 25, gap = 8;
        int count = multiSelect ? 4 : 7;
        int bw = Math.max(48, Math.min(76, (listRight() - listLeft() - gap * (count - 1)) / count));
        int x = listLeft();
        if (multiSelect) {
            Button enable = button("item_get.manager.enable_selected", x, y, bw, b -> setSelectedEnabled(true)); enable.active = editable; x += bw + gap;
            Button disable = button("item_get.manager.disable_selected", x, y, bw, b -> setSelectedEnabled(false)); disable.active = editable; x += bw + gap;
            Button delete = button("item_get.manager.delete_selected", x, y, bw, b -> deleteSelected()); delete.active = editable; x += bw + gap;
            button("gui.done", x, y, bw, b -> clearMultiSelection());
            return;
        }
        Button duplicate = button("item_get.manager.duplicate", x, y, bw, b -> duplicateSelected()); duplicate.active = editable && selected >= 0; x += bw + gap;
        Button create = button("item_get.manager.create", x, y, bw, b -> edit(new ReminderRule())); create.active = editable; x += bw + gap;
        Button edit = button("item_get.manager.edit", x, y, bw, b -> { if (selected >= 0) edit(rules.get(selected)); }); edit.active = editable && selected >= 0; x += bw + gap;
        Button test = button("item_get.manager.test", x, y, bw, b -> previewSelected()); test.active = selected >= 0; x += bw + gap;
        Button toggle = button("item_get.manager.toggle", x, y, bw, b -> toggleEnabled()); toggle.active = editable && selected >= 0; x += bw + gap;
        Button delete = button("item_get.manager.delete", x, y, bw, b -> deleteSelected()); delete.active = editable && selected >= 0; x += bw + gap;
        button("gui.done", x, y, bw, b -> onClose());
    }

    private Button button(String key, int x, int y, int w, Button.OnPress press) {
        Button button = Button.builder(Component.translatable(key), press).bounds(x, y, w, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private void cycleFilter() {
        ManagerFilter[] values = ManagerFilter.values();
        filter = values[(filter.ordinal() + 1) % values.length];
        scroll = 0;
        selected = -1;
        rebuildWidgets();
    }

    private void edit(ReminderRule rule) { minecraft.setScreen(new RuleEditorScreen(this, rule, rules.contains(rule), saved -> { if (!rules.contains(saved)) rules.add(saved); save(); })); }
    private void previewSelected(){if(selected<0||selected>=rules.size())return;ReminderRule rule=rules.get(selected);if("SIDE".equalsIgnoreCase(rule.displayStyle))SideReminderOverlay.preview(rule);else minecraft.setScreen(new ReminderScreen(rule,this));}
    private void save() { ItemGetNetwork.saveRules(new SaveRulesPacket(com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(rules)).toString())); }
    private void duplicateSelected(){if(selected<0||selected>=rules.size())return;ReminderRule copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(rules.get(selected)),ReminderRule.class);copy.id=UUID.randomUUID().toString();copy.title=copy.title==null||copy.title.isBlank()?Component.translatable("item_get.manager.unnamed_copy").getString():Component.translatable("item_get.manager.copy_title",copy.title).getString();int index=selected+1;rules.add(index,copy);selected=index;save();rebuildWidgets();}
    private void toggleEnabled(){if(selected>=0&&selected<rules.size())rules.get(selected).enabled=!rules.get(selected).enabled;save();rebuildWidgets();}
    private void setSelectedEnabled(boolean enabled){for(int i:selectedMany)if(i>=0&&i<rules.size())rules.get(i).enabled=enabled;save();clearMultiSelection();}
    private void clearMultiSelection(){selectedMany.clear();multiSelect=false;selected=-1;rebuildWidgets();}
    private void deleteSelected(){if(multiSelect){selectedMany.stream().sorted(Comparator.reverseOrder()).forEach(i->{if(i>=0&&i<rules.size())rules.remove((int)i);});selectedMany.clear();multiSelect=false;}else if(selected>=0&&selected<rules.size())rules.remove(selected);selected=-1;scroll=Math.min(scroll,maxScroll());save();rebuildWidgets();}

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && scrollbarHit(x, y)) {
            draggingScrollbar = true;
            scrollbarGrab = (int)y - scrollbarThumbTop();
            return true;
        }
        int index=rowAt(x,y);
        if(index>=0){pressedIndex=index;pressedAt=Util.getMillis();dragSelecting=false;return true;}
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&draggingScrollbar){dragScrollTo((int)y-scrollbarGrab);return true;}
        if(button==0&&pressedIndex>=0&&Util.getMillis()-pressedAt>=280){if(!dragSelecting){dragSelecting=true;multiSelect=true;selectedMany.clear();selectedMany.add(pressedIndex);}int hovered=rowAt(x,y);if(hovered>=0)selectedMany.add(hovered);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }

    @Override public boolean mouseReleased(double x,double y,int button){
        if(draggingScrollbar){draggingScrollbar=false;return true;}
        if(pressedIndex>=0){int index=pressedIndex;pressedIndex=-1;if(dragSelecting){dragSelecting=false;selected=-1;}else if(multiSelect){multiSelect=false;selectedMany.clear();selected=-1;}else selected=selected==index?-1:index;rebuildWidgets();return true;}
        return super.mouseReleased(x,y,button);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= listLeft() && x <= listRight() && y >= listTop() && y <= listBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private int rowAt(double x,double y){if(y<listTop()||y>=listBottom()||x<listLeft()||x>listRight())return-1;int row=(int)((y-listTop())/ROW_H);List<Integer> visible=visibleIndices();int pos=scroll+row;return row>=0&&row<visibleRows()&&pos>=0&&pos<visible.size()?visible.get(pos):-1;}

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        if(multiSelect)g.drawString(font,Component.translatable("item_get.manager.selected_count",selectedMany.size()),listRight()-80,controlsTop()+4,0xFFE9B0);
        List<Integer> visible = visibleIndices();
        if (visible.isEmpty()) g.drawCenteredString(font, rules.isEmpty()?Component.translatable("item_get.manager.empty"):Component.translatable("item_get.handbook.no_results"), width / 2, 86, 0x909090);
        int y = listTop(), end = Math.min(visible.size(), scroll + visibleRows());
        for (int pos = scroll; pos < end; pos++, y += ROW_H) {
            int i = visible.get(pos);
            ReminderRule rule = rules.get(i);
            boolean holding=i==pressedIndex&&Util.getMillis()-pressedAt>=250;
            g.fill(listLeft(), y, listRight(), y + 30, i == selected || selectedMany.contains(i) || holding ? 0xAA405A72 : 0x88303030);
            int iconLeft = listLeft() + 20;
            boolean image = ConfigIconLibrary.render(g, rule, iconLeft + 8, y + 15, 1F);
            if (!image) { ItemStack stack = displayStack(rule); g.renderItem(stack, iconLeft, y + 7); }
            String heading = rule.title == null || rule.title.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : TranslatedText.resolve(rule.title);
            g.drawString(font, heading, listLeft() + 60, y + 5, rule.enabled ? 0xFFFFFF : 0x888888);
            g.drawString(font, triggerSummary(rule).getString() + (rule.enabled ? "" : Component.translatable("item_get.manager.disabled_suffix").getString()), listLeft() + 60, y + 17, 0xA0A0A0);
        }
        renderScrollbar(g);
        renderCrispWidgets(g, mx, my, partial);
    }

    private List<Integer> visibleIndices() {
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            ReminderRule rule = rules.get(i);
            if (!filter.matches(rule)) continue;
            String text = (titleOf(rule) + "\n" + triggerSummary(rule).getString() + "\n" + rule.triggerType + "\n" + rule.trigger + "\n" + rule.id).toLowerCase(java.util.Locale.ROOT);
            if (q.isBlank() || text.contains(q)) out.add(i);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, out.size() - visibleRows())));
        return out;
    }

    private static String titleOf(ReminderRule rule) { return rule.title == null || rule.title.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : TranslatedText.resolve(rule.title); }
    private int controlsTop(){return 39;}
    private int listTop(){return 66;}
    private int listBottom(){return height-34;}
    private int listLeft(){return Math.max(14,width/2-410);}
    private int listRight(){return Math.min(width-18,width/2+410);}
    private int visibleRows(){return Math.max(1,(listBottom()-listTop())/ROW_H);}
    private int maxScroll(){return Math.max(0,visibleIndicesNoClamp().size()-visibleRows());}
    private List<Integer> visibleIndicesNoClamp(){String old=query;List<Integer> out=new ArrayList<>();String q=old.trim().toLowerCase(java.util.Locale.ROOT);for(int i=0;i<rules.size();i++){ReminderRule rule=rules.get(i);if(!filter.matches(rule))continue;String text=(titleOf(rule)+"\n"+triggerSummary(rule).getString()+"\n"+rule.triggerType+"\n"+rule.trigger+"\n"+rule.id).toLowerCase(java.util.Locale.ROOT);if(q.isBlank()||text.contains(q))out.add(i);}return out;}
    private boolean scrollbarHit(double x,double y){return maxScroll()>0&&x>=scrollbarX()-2&&x<=scrollbarX()+5&&y>=listTop()&&y<=listBottom();}
    private int scrollbarX(){return listRight()+5;}
    private int scrollbarThumbTop(){int track=listBottom()-listTop(),thumb=scrollbarThumbHeight();return listTop()+(track-thumb)*scroll/Math.max(1,maxScroll());}
    private int scrollbarThumbHeight(){int track=listBottom()-listTop(),total=visibleIndicesNoClamp().size();return Math.max(18,total<=0?track:track*visibleRows()/Math.max(visibleRows(),total));}
    private void dragScrollTo(int y){int track=listBottom()-listTop(),thumb=scrollbarThumbHeight();scroll=Math.max(0,Math.min(maxScroll(),(y-listTop())*Math.max(1,maxScroll())/Math.max(1,track-thumb)));}
    private void renderScrollbar(GuiGraphics g){if(maxScroll()<=0)return;int x=scrollbarX(),top=listTop(),bottom=listBottom(),thumbTop=scrollbarThumbTop();g.fill(x,top,x+1,bottom,0x55FFFFFF);g.fill(x-1,thumbTop,x+3,thumbTop+scrollbarThumbHeight(),0xAAFFFFFF);}

    static ItemStack item(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
    private static ItemStack triggerStack(ReminderRule rule, ItemStack fallback) {
        if (rule.trigger.has("stack")) try {
            ItemStack stack = ItemStack.parseOptional(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), TagParser.parseTag(rule.trigger.get("stack").getAsString()));
            if (!stack.isEmpty()) return stack;
        } catch (Exception ignored) {}
        return fallback;
    }
    public static ItemStack displayStack(ReminderRule rule) {
        if (rule.iconStack != null && !rule.iconStack.isBlank()) try {
            ItemStack stack = ItemStack.parseOptional(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), TagParser.parseTag(rule.iconStack));
            if (!stack.isEmpty()) return stack;
        } catch (Exception ignored) {}
        if (rule.icon != null && !rule.icon.isBlank() && !item(rule.icon).isEmpty()) return item(rule.icon);
        TriggerType type = TriggerType.parse(rule.triggerType);
        if (type == TriggerType.ITEM_ACQUIRED || type == TriggerType.HOVER_ITEM) return triggerStack(rule, item(rule.target()));
        return switch (type) { case ITEM_ACQUIRED -> item(rule.target()); case ENTITY_KILLED -> new ItemStack(Items.IRON_SWORD); case HEALTH_AT -> new ItemStack(Items.GLISTERING_MELON_SLICE); case HUNGER_AT -> new ItemStack(Items.BREAD); case EFFECT_GAINED -> new ItemStack(Items.POTION); case WEATHER_IS -> new ItemStack(Items.WATER_BUCKET); case TIME_IS -> new ItemStack(Items.CLOCK); case ENTER_BIOME -> new ItemStack(Items.GRASS_BLOCK); case ENTER_STRUCTURE -> new ItemStack(Items.FILLED_MAP); case DIMENSION_CHANGED -> new ItemStack(Items.COMPASS); case DEATH_BY -> new ItemStack(Items.SKELETON_SKULL); case ADVANCEMENT_DONE -> new ItemStack(Items.WRITABLE_BOOK); case OBSERVE_BLOCK -> item(rule.trigger.has("block") ? rule.trigger.get("block").getAsString() : "minecraft:oak_log"); case OBSERVE_ENTITY -> new ItemStack(Items.SPYGLASS); case HOVER_ITEM -> item(rule.target()); case MANUAL -> new ItemStack(Items.COMMAND_BLOCK); };
    }
    static Component targetName(ReminderRule rule) {
        TriggerType type = TriggerType.parse(rule.triggerType);
        if (type == TriggerType.ITEM_ACQUIRED) return triggerStack(rule, item(rule.target())).getHoverName();
        if (type == TriggerType.ENTITY_KILLED) { var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(rule.entityTarget())); return entity == null ? Component.literal(rule.entityTarget()) : entity.getDescription(); }
        if (type == TriggerType.OBSERVE_BLOCK) return item(rule.trigger.has("block") ? rule.trigger.get("block").getAsString() : "minecraft:oak_log").getHoverName();
        if (type == TriggerType.OBSERVE_ENTITY) { var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(rule.entityTarget())); return entity == null ? Component.literal(rule.entityTarget()) : entity.getDescription(); }
        if (type == TriggerType.HOVER_ITEM) return triggerStack(rule, item(rule.target())).getHoverName();
        String key = switch(type){case HEALTH_AT,HUNGER_AT->"value";case EFFECT_GAINED->"effect";case WEATHER_IS->"weather";case TIME_IS->"time";case ENTER_BIOME->"biome";case ENTER_STRUCTURE->"structure";case DIMENSION_CHANGED->"dimension";case DEATH_BY->"death";case ADVANCEMENT_DONE->"advancement";case MANUAL->"manual";default->"";};
        String id = rule.trigger.has(key)?rule.trigger.get(key).getAsString():type.name();
        if(type==TriggerType.EFFECT_GAINED){var effect=BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(id));if(effect!=null)return effect.getDisplayName();}
        if(type==TriggerType.WEATHER_IS||type==TriggerType.TIME_IS)return Component.translatable("item_get.choice."+id);
        if(type==TriggerType.ENTER_BIOME||type==TriggerType.ENTER_STRUCTURE){ResourceLocation location=ResourceLocation.tryParse(id);if(location!=null){String prefix=type==TriggerType.ENTER_BIOME?"biome":"structure";String translation=prefix+"."+location.getNamespace()+"."+location.getPath().replace('/','.');if(I18n.exists(translation))return Component.literal(I18n.get(translation));}}
        if(type==TriggerType.DIMENSION_CHANGED){ResourceLocation location=ResourceLocation.tryParse(id);if(location!=null){String translation="dimension."+location.getNamespace()+"."+location.getPath().replace('/','.');if(I18n.exists(translation))return Component.literal(I18n.get(translation));}}
        if(type==TriggerType.DEATH_BY)return Component.literal(ClientHooks.damageTypeName(id));
        if(type==TriggerType.ADVANCEMENT_DONE)return Component.literal(ClientHooks.advancementName(id));
        if(type==TriggerType.MANUAL)return Component.literal(id);
        return Component.literal(id);
    }
    public static Component triggerSummary(ReminderRule rule) {
        if (RuleConditions.entries(rule).size() > 1) return Component.translatable("item_get.summary.conditions", Component.translatable("OR".equals(RuleConditions.logic(rule)) ? "item_get.editor.logic_any" : "item_get.editor.logic_all"), RuleConditions.entries(rule).size());
        return singleTriggerSummary(rule);
    }
    public static Component displaySubtitle(ReminderRule rule, long timeMs) {
        if (rule.subtitle != null && !rule.subtitle.isBlank()) return TranslatedText.component(rule.subtitle);
        List<RuleConditions.Entry> entries = RuleConditions.entries(rule);
        if (entries.size() <= 1) return singleTriggerSummary(rule);
        RuleConditions.Entry entry = entries.get((int)((Math.max(0L, timeMs) / 1800L) % entries.size()));
        return singleTriggerSummary(conditionRule(entry));
    }
    private static Component singleTriggerSummary(ReminderRule rule) {
        TriggerType type = TriggerType.parse(rule.triggerType); String name = targetName(rule).getString();
        boolean counted = type == TriggerType.ITEM_ACQUIRED || type == TriggerType.ENTITY_KILLED;
        if (type == TriggerType.OBSERVE_BLOCK || type == TriggerType.OBSERVE_ENTITY || type == TriggerType.HOVER_ITEM)
            return Component.translatable("item_get.summary."+type.name().toLowerCase(java.util.Locale.ROOT), name, String.format(java.util.Locale.ROOT, "%.1f", rule.threshold() / 20.0));
        String key="item_get.summary."+type.name().toLowerCase(java.util.Locale.ROOT)+(counted && rule.threshold()==1?".one":"");
        return (type==TriggerType.ITEM_ACQUIRED||type==TriggerType.ENTITY_KILLED)?Component.translatable(key,name,rule.threshold()):Component.translatable(key,name,rule.trigger.has("value")?rule.trigger.get("value").getAsString():"");
    }
    private static ReminderRule conditionRule(RuleConditions.Entry entry) {
        ReminderRule out = new ReminderRule();
        out.triggerType = entry.type().name();
        out.trigger = entry.data().deepCopy();
        return out;
    }

    private enum ManagerFilter {
        ALL("item_get.handbook.filter.all"),
        ITEMS("item_get.handbook.filter.items"),
        ENTITY("item_get.handbook.filter.entity"),
        WORLD("item_get.handbook.filter.world"),
        PLAYER("item_get.handbook.filter.player"),
        ADVANCEMENT("item_get.handbook.filter.advancement"),
        PONDER("item_get.handbook.filter.ponder"),
        ENABLED("item_get.manager.filter.enabled"),
        DISABLED("item_get.manager.filter.disabled");

        private final String key;
        ManagerFilter(String key) { this.key = key; }
        private boolean matches(ReminderRule rule) {
            TriggerType type = TriggerType.parse(rule.triggerType);
            return switch (this) {
                case ALL -> true;
                case ITEMS -> type == TriggerType.ITEM_ACQUIRED;
                case ENTITY -> type == TriggerType.ENTITY_KILLED || type == TriggerType.OBSERVE_ENTITY;
                case WORLD -> type == TriggerType.WEATHER_IS || type == TriggerType.TIME_IS || type == TriggerType.ENTER_BIOME || type == TriggerType.ENTER_STRUCTURE || type == TriggerType.DIMENSION_CHANGED || type == TriggerType.MANUAL;
                case PLAYER -> type == TriggerType.HEALTH_AT || type == TriggerType.HUNGER_AT || type == TriggerType.EFFECT_GAINED || type == TriggerType.DEATH_BY || type == TriggerType.OBSERVE_BLOCK || type == TriggerType.HOVER_ITEM;
                case ADVANCEMENT -> type == TriggerType.ADVANCEMENT_DONE;
                case PONDER -> ClientHooks.hasPonderScene(rule.ponderTarget);
                case ENABLED -> rule.enabled;
                case DISABLED -> !rule.enabled;
            };
        }
    }
}
