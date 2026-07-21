package com.kuzhi.itemget.client;

import com.kuzhi.itemget.client.screen.ManagerScreen;
import com.kuzhi.itemget.client.screen.HandbookScreen;
import com.kuzhi.itemget.client.screen.ReminderScreen;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public final class ClientHooks {
    private static final Queue<ReminderRule> PENDING = new ArrayDeque<>();
    private static List<ReminderRule> LAST_HISTORY = List.of();
    private static List<ReminderRule> OBSERVER_RULES = List.of();
    private static List<String> BIOMES = List.of(), STRUCTURES = List.of(), DAMAGE_TYPES = List.of(), ADVANCEMENTS = List.of();
    private static Map<String, String> DAMAGE_NAMES = Map.of(), ADVANCEMENT_NAMES = Map.of();
    private static int inventoryButtonX = -1, inventoryButtonY = -1;
    private static boolean uiLayoutRequested;
    public static void openManager(List<ReminderRule> rules, boolean editable, String biomes, String structures, String damageTypes, String advancements) {
        BIOMES = lines(biomes); STRUCTURES = lines(structures); DAMAGE_TYPES = ids(damageTypes); ADVANCEMENTS = ids(advancements);
        DAMAGE_NAMES = damageNames(damageTypes); ADVANCEMENT_NAMES = names(advancements);
        syncObserverRules(rules);
        Minecraft.getInstance().setScreen(new ManagerScreen(rules, editable));
    }
    public static void syncObserverRules(List<ReminderRule> rules) { OBSERVER_RULES = rules == null ? List.of() : List.copyOf(rules); }
    public static List<ReminderRule> observerRules() { return OBSERVER_RULES; }
    public static void openHandbook(List<ReminderRule> history) {
        LAST_HISTORY = List.copyOf(history);
        Minecraft.getInstance().setScreen(new HandbookScreen(history));
    }
    public static void openCachedHandbook() {
        if (!LAST_HISTORY.isEmpty()) Minecraft.getInstance().setScreen(new HandbookScreen(LAST_HISTORY));
    }
    public static boolean hasPonder() {
        return hasPonderer() || hasCreatePonder();
    }
    public static boolean hasPonderer() { return hasClass("com.nododiiiii.ponderer.ui.PonderItemGridScreen"); }
    public static boolean hasCreatePonder() { return hasClass("net.createmod.ponder.command.SimplePonderActions"); }
    public static boolean hasPonderScene(String target) {
        return hasPondererScene(target) || hasCreatePonderScene(target);
    }
    public static boolean hasCreatePonderScene(String target) {
        if (!hasCreatePonder() || target == null || target.isBlank()) return false;
        ResourceLocation location = ResourceLocation.tryParse(target.trim());
        if (location == null) return false;
        try {
            Object access = Class.forName("net.createmod.ponder.foundation.PonderIndex").getMethod("getSceneAccess").invoke(null);
            return Boolean.TRUE.equals(access.getClass().getMethod("doScenesExistForId", ResourceLocation.class).invoke(access, location));
        } catch (Throwable ignored) {
            return false;
        }
    }
    public static boolean hasPondererScene(String target) {
        if (!hasPonderer() || target == null || target.isBlank()) return false;
        String cleaned = target.trim();
        try {
            if (findPondererScene(cleaned) != null) return true;
            Class<?> runtime = Class.forName("com.nododiiiii.ponderer.ponder.SceneRuntime");
            Object refs = runtime.getMethod("resolvePonderSceneRefs", Collection.class).invoke(null, List.of(cleaned));
            if (refs instanceof Collection<?> collection && !collection.isEmpty()) return true;
            Object scenes = runtime.getMethod("getScenes").invoke(null);
            if (scenes instanceof Collection<?> collection) {
                for (Object scene : collection) {
                    Object items = scene.getClass().getField("items").get(scene);
                    if (items instanceof Collection<?> ids) for (Object id : ids) if (cleaned.equals(String.valueOf(id))) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
    public static boolean openPonder(String target) {
        if (target == null || target.isBlank()) return false;
        String cleaned = target.trim();
        if (openPonderer(cleaned)) return true;
        return openCreatePonder(cleaned);
    }
    public static boolean openPonderSelector(Screen parent, Consumer<String> selected, Runnable canceled) {
        if (!hasPonderer()) return false;
        try {
            Class<?> screenClass = Class.forName("com.nododiiiii.ponderer.ui.PonderItemGridScreen");
            Object screen = screenClass.getConstructor(Screen.class, Consumer.class, Runnable.class).newInstance(parent, selected, canceled);
            return openScreen(screen);
        } catch (Throwable ignored) {
            return false;
        }
    }
    private static boolean hasClass(String name) {
        try { Class.forName(name); return true; }
        catch (Throwable ignored) { return false; }
    }
    public static boolean openCreatePonder(String target) {
        if (!hasCreatePonderScene(target)) return false;
        try {
            Class<?> actions = Class.forName("net.createmod.ponder.command.SimplePonderActions");
            actions.getMethod("openPonder", String.class).invoke(null, target.trim());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
    public static boolean openPonderer(String target) {
        if (!hasPondererScene(target)) return false;
        String cleaned = target.trim();
        try {
            Object scene = findPondererScene(cleaned);
            String sceneKey = scene == null ? "" : sceneKey(scene);
            String itemId = scene == null ? cleaned : firstSceneItem(scene);
            ItemStack stack = itemStack(itemId);
            if (stack.isEmpty()) return openPondererGrid();
            Object ui = Class.forName("net.createmod.ponder.foundation.ui.PonderUI").getMethod("of", ItemStack.class).invoke(null, stack);
            if (scene != null && sceneKey != null && !sceneKey.isBlank()) filterPondererUi(ui, sceneKey);
            return openScreen(ui);
        } catch (Throwable ignored) {
            return openPondererGrid();
        }
    }
    private static boolean openPondererGrid() {
        try {
            Object screen = Class.forName("com.nododiiiii.ponderer.ui.PonderItemGridScreen").getConstructor().newInstance();
            return openScreen(screen);
        } catch (Throwable ignored) {
            return false;
        }
    }
    private static Object findPondererScene(String target) throws ReflectiveOperationException {
        Class<?> runtime = Class.forName("com.nododiiiii.ponderer.ponder.SceneRuntime");
        Object scene = runtime.getMethod("findByKey", String.class).invoke(null, target);
        if (scene != null) return scene;
        Object scenes = runtime.getMethod("getScenes").invoke(null);
        if (scenes instanceof Collection<?> collection) {
            for (Object candidate : collection) {
                Object id = candidate.getClass().getField("id").get(candidate);
                if (target.equals(id)) return candidate;
            }
        }
        return null;
    }
    private static String sceneKey(Object scene) throws ReflectiveOperationException {
        Object value = scene.getClass().getMethod("sceneKey").invoke(scene);
        return value == null ? "" : value.toString();
    }
    private static String firstSceneItem(Object scene) throws ReflectiveOperationException {
        Object value = scene.getClass().getField("items").get(scene);
        if (value instanceof Collection<?> items) {
            for (Object item : items) return item == null ? "" : item.toString();
        }
        return "";
    }
    private static void filterPondererUi(Object ui, String sceneKey) {
        try {
            Class<?> runtime = Class.forName("com.nododiiiii.ponderer.ponder.SceneRuntime");
            Object refs = runtime.getMethod("resolvePonderSceneRefs", Collection.class).invoke(null, List.of(sceneKey));
            if (!(refs instanceof Collection<?> refSet) || refSet.isEmpty()) return;
            Class<?> accessor = Class.forName("com.nododiiiii.ponderer.mixin.PonderUIAccessor");
            Object scenesObject = accessor.getMethod("ponderer$getScenes").invoke(accessor.cast(ui));
            if (!(scenesObject instanceof List<?> scenes)) return;
            Class<?> refClass = Class.forName("com.nododiiiii.ponderer.ponder.SceneRuntime$PonderSceneRef");
            var refCtor = refClass.getConstructor(String.class, int.class);
            Map<String, Integer> counts = new HashMap<>();
            List<Object> filtered = new ArrayList<>();
            for (Object ponderScene : scenes) {
                String id = String.valueOf(ponderScene.getClass().getMethod("getId").invoke(ponderScene));
                int occurrence = counts.getOrDefault(id, 0);
                counts.put(id, occurrence + 1);
                if (refSet.contains(refCtor.newInstance(id, occurrence))) filtered.add(ponderScene);
            }
            if (!filtered.isEmpty() && filtered.size() < scenes.size()) {
                @SuppressWarnings("unchecked")
                List<Object> mutableScenes = (List<Object>) scenesObject;
                mutableScenes.clear();
                mutableScenes.addAll(filtered);
            }
        } catch (Throwable ignored) {
        }
    }
    private static ItemStack itemStack(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id);
        if (location == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
    private static boolean openScreen(Object screen) {
        if (!(screen instanceof Screen mcScreen)) return false;
        try {
            Class.forName("net.createmod.catnip.gui.ScreenOpener").getMethod("open", Screen.class).invoke(null, mcScreen);
        } catch (Throwable ignored) {
            Minecraft.getInstance().setScreen(mcScreen);
        }
        return true;
    }
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
    public static void show(ReminderRule rule) {
        if ("SIDE".equalsIgnoreCase(rule.displayStyle)) SideReminderOverlay.push(rule);
        else { PENDING.add(rule); tick(); }
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { resetUiLayoutRequest(); return; }
        ensureUiLayoutRequested();
        SideReminderOverlay.tick();
        if (!PENDING.isEmpty() && mc.player != null && mc.screen == null) mc.setScreen(new ReminderScreen(PENDING.remove(), null));
    }
}
