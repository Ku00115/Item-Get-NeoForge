package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.TriggerType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.kuzhi.itemget.client.ClientHooks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class RuleEditorScreen extends CrispScreen {
    private final Screen parent; private final ReminderRule original; private final ReminderRule rule; private final Consumer<ReminderRule> saver;
    private EditBox count, titleBox, description; private int left, panelWidth, top, step;
    public RuleEditorScreen(Screen parent, ReminderRule rule, boolean existing, Consumer<ReminderRule> saver) { super(Component.translatable(existing?"item_get.editor.edit_title":"item_get.editor.create_title"));this.parent=parent;this.original=rule;this.rule=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(rule),ReminderRule.class);this.saver=saver;if(this.rule.sound==null||this.rule.sound.isBlank())this.rule.sound="item_get:item_acquired"; }

    @Override protected void init(){panelWidth=Math.min(380,width-24);left=(width-panelWidth)/2;top=height<290?34:42;step=height<290?27:30;int y=top;int arrow=26;
        addRenderableWidget(Button.builder(Component.literal("‹"),b->{read();cycle(-1);}).bounds(left,y,arrow,20).build());
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.trigger",Component.translatable(type().translationKey)),b->openTypeList()).bounds(left+arrow+4,y,panelWidth-arrow*2-8,20).build());
        addRenderableWidget(Button.builder(Component.literal("›"),b->{read();cycle(1);}).bounds(left+panelWidth-arrow,y,arrow,20).build());
        y+=step;addRenderableWidget(Button.builder(Component.literal(targetLabel()),b->openTarget()).bounds(left,y,panelWidth,20).build());
        y+=step;
        if(counted()){count=new EditBox(font,left+58,y,62,20,Component.translatable("item_get.editor.count"));count.setValue(Integer.toString(rule.threshold()));addRenderableWidget(count);titleBox=new EditBox(font,left+164,y,panelWidth-164,20,Component.translatable("item_get.editor.title"));}
        else {count=null;titleBox=new EditBox(font,left+58,y,panelWidth-58,20,Component.translatable("item_get.editor.title"));}
        titleBox.setValue(rule.title==null?"":rule.title);titleBox.setMaxLength(120);addRenderableWidget(titleBox);
        y+=step;description=new EditBox(font,left+58,y,panelWidth-58,20,Component.translatable("item_get.editor.description"));description.setValue(rule.description==null?"":rule.description);description.setMaxLength(512);addRenderableWidget(description);
        y+=step;int half=(panelWidth-4)/2;
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.icon",iconName()),b->{read();minecraft.setScreen(new ItemPickerScreen(this,stack->{rule.icon=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();rule.iconStack=stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString();minecraft.setScreen(this);}));}).bounds(left,y,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.sound"),b->{read();minecraft.setScreen(new SoundPickerScreen(this,id->{rule.sound=id;minecraft.setScreen(this);}));}).bounds(left+half+4,y,half,20).build());
        int buttons=Math.min(height-46,y+step+1);
        addRenderableWidget(Button.builder(Component.translatable(rule.pauseSingleplayer?"item_get.editor.pause_on":"item_get.editor.pause_off"),b->{rule.pauseSingleplayer=!rule.pauseSingleplayer;b.setMessage(Component.translatable(rule.pauseSingleplayer?"item_get.editor.pause_on":"item_get.editor.pause_off"));}).bounds(left,buttons,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.preview"),b->{read();minecraft.setScreen(new ReminderScreen(rule,this));}).bounds(left+half+4,buttons,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("item_get.editor.save"),b->{read();applyDraft();saver.accept(original);minecraft.setScreen(parent);}).bounds(left,buttons+23,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->minecraft.setScreen(parent)).bounds(left+half+4,buttons+23,half,20).build());
    }

    private TriggerType type(){return TriggerType.parse(rule.triggerType);} private boolean counted(){return type()==TriggerType.ITEM_ACQUIRED||type()==TriggerType.ENTITY_KILLED;}
    private void cycle(int d){TriggerType[] v=TriggerType.values();setType(v[Math.floorMod(type().ordinal()+d,v.length)]);}
    private void setType(TriggerType t){rule.triggerType=t.name();defaults(t);rebuildWidgets();}
    private void defaults(TriggerType t){switch(t){case ITEM_ACQUIRED->put("item","minecraft:diamond");case ENTITY_KILLED->put("entity","minecraft:zombie");case HEALTH_AT->put("value",10);case HUNGER_AT->put("value",10);case EFFECT_GAINED->put("effect","minecraft:speed");case WEATHER_IS->put("weather","rain");case TIME_IS->put("time","day");case ENTER_BIOME->put("biome","minecraft:plains");case ENTER_STRUCTURE->put("structure","minecraft:village_plains");}}
    private void put(String k,String v){if(!rule.trigger.has(k))rule.trigger.addProperty(k,v);}private void put(String k,double v){if(!rule.trigger.has(k))rule.trigger.addProperty(k,v);}
    private void openTypeList(){read();List<SelectionListScreen.Entry> e=new ArrayList<>();for(TriggerType t:TriggerType.values())e.add(new SelectionListScreen.Entry(t.name(),Component.translatable(t.translationKey).getString()));minecraft.setScreen(new SelectionListScreen(this,tr("item_get.editor.choose_trigger"),e,id->{setType(TriggerType.valueOf(id));}));}

    private String targetLabel(){return switch(type()){
        case ITEM_ACQUIRED->tr("item_get.editor.item",ManagerScreen.item(rule.target()).getHoverName().getString());case ENTITY_KILLED->tr("item_get.editor.entity",entityName(value("entity","minecraft:zombie")));
        case HEALTH_AT->tr("item_get.editor.health",value("value","10"));case HUNGER_AT->tr("item_get.editor.hunger",value("value","10"));case EFFECT_GAINED->tr("item_get.editor.effect",effectName(value("effect","minecraft:speed")));
        case WEATHER_IS->tr("item_get.editor.weather",choiceName(value("weather","rain")));case TIME_IS->tr("item_get.editor.time",choiceName(value("time","day")));case ENTER_BIOME->tr("item_get.editor.biome",translated("biome",value("biome","minecraft:plains")));case ENTER_STRUCTURE->tr("item_get.editor.structure",translated("structure",value("structure","minecraft:village_plains")));};}
    private void openTarget(){read();switch(type()){
        case ITEM_ACQUIRED->minecraft.setScreen(new ItemPickerScreen(this,stack->{rule.trigger.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());minecraft.setScreen(this);}));
        case ENTITY_KILLED->minecraft.setScreen(new EntityPickerScreen(this,id->{rule.trigger.addProperty("entity",id);minecraft.setScreen(this);}));
        case HEALTH_AT,HUNGER_AT->minecraft.setScreen(new NumberInputScreen(this,tr(type()==TriggerType.HEALTH_AT?"item_get.editor.health_threshold":"item_get.editor.hunger_threshold"),value("value","10"),v->{try{rule.trigger.addProperty("value",Double.parseDouble(v));}catch(Exception ignored){}minecraft.setScreen(this);}));
        case EFFECT_GAINED->openEntries(tr("item_get.editor.choose_effect"),effects(),"effect");case WEATHER_IS->openEntries(tr("item_get.editor.choose_weather"),List.of(entry("clear",choiceName("clear")),entry("rain",choiceName("rain")),entry("thunder",choiceName("thunder"))),"weather");
        case TIME_IS->openEntries(tr("item_get.editor.choose_time"),List.of(entry("day",choiceName("day")),entry("noon",choiceName("noon")),entry("night",choiceName("night")),entry("midnight",choiceName("midnight"))),"time");case ENTER_BIOME->openEntries(tr("item_get.editor.choose_biome"),catalog(ClientHooks.biomes(),"biome"),"biome");case ENTER_STRUCTURE->openEntries(tr("item_get.editor.choose_structure"),catalog(ClientHooks.structures(),"structure"),"structure");}}
    private void openEntries(String title,List<SelectionListScreen.Entry> entries,String key){minecraft.setScreen(new SelectionListScreen(this,title,entries,id->{rule.trigger.addProperty(key,id);minecraft.setScreen(this);}));}
    private List<SelectionListScreen.Entry> effects(){List<SelectionListScreen.Entry> out=new ArrayList<>();BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e->{String id=e.getKey().location().toString();out.add(entry(id,e.getValue().getDisplayName().getString()));});out.sort(Comparator.comparing(SelectionListScreen.Entry::name));return out;}
    private List<SelectionListScreen.Entry> catalog(List<String> ids,String prefix){List<SelectionListScreen.Entry> out=new ArrayList<>();ids.forEach(id->out.add(entry(id,translated(prefix,id))));out.sort(Comparator.comparing(SelectionListScreen.Entry::name));return out;}
    private void read(){if(count!=null){try{rule.trigger.addProperty("count",Math.max(1,Integer.parseInt(count.getValue())));}catch(Exception ignored){rule.trigger.addProperty("count",1);}}rule.title=titleBox.getValue();rule.description=description.getValue();}
    private void applyDraft(){original.id=rule.id;original.triggerType=rule.triggerType;original.trigger=rule.trigger.deepCopy();original.title=rule.title;original.description=rule.description;original.icon=rule.icon;original.iconStack=rule.iconStack;original.displayStyle=rule.displayStyle;original.sound=rule.sound;original.music=rule.music;original.pauseSingleplayer=rule.pauseSingleplayer;original.enabled=rule.enabled;}
    private String iconName(){ItemStack stack=ManagerScreen.displayStack(rule);return rule.icon==null||rule.icon.isBlank()?tr("item_get.editor.auto"):stack.getHoverName().getString();}
    private String value(String k,String f){return rule.trigger.has(k)?rule.trigger.get(k).getAsString():f;}private static SelectionListScreen.Entry entry(String id,String name){return new SelectionListScreen.Entry(id,name);}
    private String entityName(String id){var t=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(id));return t==null?id:t.getDescription().getString();}private String effectName(String id){var e=BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(id));return e==null?id:e.getDisplayName().getString();}
    private String translated(String prefix,String id){ResourceLocation r=ResourceLocation.tryParse(id);if(r==null)return id;String key=prefix+"."+r.getNamespace()+"."+r.getPath().replace('/','.');return I18n.exists(key)?I18n.get(key):id;}
    private String choiceName(String id){return tr("item_get.choice."+id);}
    private static String tr(String key,Object...args){return Component.translatable(key,args).getString();}
    @Override public void render(GuiGraphics g,int mx,int my,float p){g.fill(0, 0, width, height, 0x88000000);g.drawCenteredString(font,title,width/2,8,0xFFFFFF);int y=top+step*2;if(count!=null){g.drawString(font,Component.translatable("item_get.editor.count"),left,y+6,0xD8D8D8);g.drawString(font,Component.translatable("item_get.editor.title"),left+125,y+6,0xD8D8D8);}else g.drawString(font,Component.translatable("item_get.editor.title"),left,y+6,0xD8D8D8);g.drawString(font,Component.translatable("item_get.editor.description"),left,y+step+6,0xD8D8D8);renderCrispWidgets(g,mx,my,p);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
