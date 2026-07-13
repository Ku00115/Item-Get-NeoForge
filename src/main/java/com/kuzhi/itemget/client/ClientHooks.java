package com.kuzhi.itemget.client;

import com.kuzhi.itemget.client.screen.ManagerScreen;
import com.kuzhi.itemget.client.screen.HandbookScreen;
import com.kuzhi.itemget.client.screen.ReminderScreen;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.network.chat.Component;

public final class ClientHooks {
    private static final Queue<ReminderRule> PENDING = new ArrayDeque<>();
    private static List<String> BIOMES = List.of(), STRUCTURES = List.of(), DAMAGE_TYPES = List.of(), ADVANCEMENTS = List.of();
    private static Map<String, String> DAMAGE_NAMES = Map.of(), ADVANCEMENT_NAMES = Map.of();
    private static int inventoryButtonX = -1, inventoryButtonY = -1;
    private static boolean uiLayoutRequested;
    public static void openManager(List<ReminderRule> rules, boolean editable, String biomes, String structures, String damageTypes, String advancements) {
        BIOMES = lines(biomes); STRUCTURES = lines(structures); DAMAGE_TYPES = ids(damageTypes); ADVANCEMENTS = ids(advancements);
        DAMAGE_NAMES = damageNames(damageTypes); ADVANCEMENT_NAMES = names(advancements);
        Minecraft.getInstance().setScreen(new ManagerScreen(rules, editable));
    }
    public static void openHandbook(List<ReminderRule> history) { Minecraft.getInstance().setScreen(new HandbookScreen(history)); }
    public static void setInventoryButtonPosition(int x, int y) { inventoryButtonX = x; inventoryButtonY = y; }
    public static int inventoryButtonX() { return inventoryButtonX; }
    public static int inventoryButtonY() { return inventoryButtonY; }
    public static void resetUiLayoutRequest() { uiLayoutRequested = false; inventoryButtonX = -1; inventoryButtonY = -1; }
    public static void ensureUiLayoutRequested() {
        if (!uiLayoutRequested) {
            uiLayoutRequested = true;
            com.kuzhi.itemget.network.ItemGetNetwork.requestUiLayout();
        }
    }
    private static List<String> lines(String value){return value==null||value.isBlank()?List.of():Arrays.stream(value.split("\n")).filter(s->!s.isBlank()).toList();}
    private static List<String> ids(String value){return lines(value).stream().map(line->line.split("\t",2)[0]).toList();}
    private static Map<String,String> names(String value){Map<String,String> out=new LinkedHashMap<>();for(String line:lines(value)){String[] p=line.split("\t",2);out.put(p[0],p.length>1&&!p[1].isBlank()?p[1]:p[0]);}return out;}
    private static Map<String,String> damageNames(String value){Map<String,String> out=new LinkedHashMap<>();for(String line:lines(value)){String[] p=line.split("\t",2);String msg=p.length>1&&!p[1].isBlank()?p[1]:p[0];out.put(p[0],Component.translatable("death.attack."+msg).getString());}return out;}
    public static List<String> biomes(){return BIOMES;}public static List<String> structures(){return STRUCTURES;}public static List<String> damageTypes(){return DAMAGE_TYPES;}public static List<String> advancements(){return ADVANCEMENTS;}
    public static String damageTypeName(String id){return DAMAGE_NAMES.getOrDefault(id,id);}public static String advancementName(String id){return ADVANCEMENT_NAMES.getOrDefault(id,id);}
    public static void show(ReminderRule rule) { PENDING.add(rule); tick(); }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { resetUiLayoutRequest(); return; }
        ensureUiLayoutRequested();
        if (!PENDING.isEmpty() && mc.player != null && mc.screen == null) mc.setScreen(new ReminderScreen(PENDING.remove(), null));
    }
}
