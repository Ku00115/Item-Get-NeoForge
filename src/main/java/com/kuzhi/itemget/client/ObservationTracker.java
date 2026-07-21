package com.kuzhi.itemget.client;

import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleConditions;
import com.kuzhi.itemget.rule.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class ObservationTracker {
    private static final Map<String, Integer> PROGRESS = new HashMap<>();
    private static final Map<String, Long> SENT_AT = new HashMap<>();
    private static String lastHoverItem = "";
    private static ItemStack lastHoverStack = ItemStack.EMPTY;

    private ObservationTracker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientHooks.observerRules().isEmpty()) {
            PROGRESS.clear();
            return;
        }
        if (mc.screen != null) {
            tickHoverItem();
            return;
        }
        HitResult hit = mc.hitResult;
        String block = "", entity = "";
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(blockHit.getBlockPos()).getBlock());
            if (id != null) block = id.toString();
        } else if (hit instanceof EntityHitResult entityHit) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType());
            if (id != null) entity = id.toString();
        }
        tickKind(TriggerType.OBSERVE_BLOCK, block);
        tickKind(TriggerType.OBSERVE_ENTITY, entity);
    }

    public static void captureHoveredItem(Screen screen) {
        lastHoverItem = "";
        lastHoverStack = ItemStack.EMPTY;
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        Slot slot = hoveredSlot(container);
        if (slot == null || !slot.hasItem()) return;
        ItemStack stack = slot.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) { lastHoverItem = id.toString(); lastHoverStack = stack.copy(); }
    }

    private static void tickHoverItem() {
        long now = Util.getMillis();
        for (ReminderRule rule : ClientHooks.observerRules()) {
            if (!rule.enabled) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (condition.type() != TriggerType.HOVER_ITEM) continue;
                String key = progressKey(rule, condition);
                boolean matches = !lastHoverStack.isEmpty() && lastHoverItem.equals(expectedTarget(condition, TriggerType.HOVER_ITEM)) && nbtMatches(condition, lastHoverStack);
                int ticks = matches ? PROGRESS.getOrDefault(key, 0) + 1 : 0;
                if (ticks <= 0) PROGRESS.remove(key); else PROGRESS.put(key, ticks);
                if (ticks >= threshold(condition) && now - SENT_AT.getOrDefault(rule.id, 0L) > 3000L) {
                    SENT_AT.put(rule.id, now);
                    ItemGetNetwork.triggerObserved(rule.id);
                }
            }
        }
    }

    private static void tickKind(TriggerType type, String target) {
        long now = Util.getMillis();
        for (ReminderRule rule : ClientHooks.observerRules()) {
            if (!rule.enabled) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (condition.type() != type) continue;
                String key = progressKey(rule, condition);
                String expected = expectedTarget(condition, type);
                boolean matches = !target.isBlank() && target.equals(expected);
                int ticks = matches ? PROGRESS.getOrDefault(key, 0) + 1 : 0;
                if (ticks <= 0) PROGRESS.remove(key); else PROGRESS.put(key, ticks);
                if (ticks >= threshold(condition) && now - SENT_AT.getOrDefault(rule.id, 0L) > 3000L) {
                    SENT_AT.put(rule.id, now);
                    ItemGetNetwork.triggerObserved(rule.id);
                }
            }
        }
    }

    private static String progressKey(ReminderRule rule, RuleConditions.Entry condition) {
        return rule.id + "#c" + condition.index();
    }

    private static int threshold(RuleConditions.Entry condition) {
        try { return Math.max(1, condition.data().has("count") ? condition.data().get("count").getAsInt() : 1); }
        catch (Exception ignored) { return 1; }
    }

    private static String expectedTarget(RuleConditions.Entry condition, TriggerType type) {
        String key = switch (type) {
            case OBSERVE_BLOCK -> "block";
            case OBSERVE_ENTITY -> "entity";
            case HOVER_ITEM -> "item";
            default -> "item";
        };
        return condition.data().has(key) ? condition.data().get(key).getAsString() : "";
    }

    private static boolean nbtMatches(RuleConditions.Entry condition, ItemStack stack) {
        JsonObject data = condition.data();
        if (!data.has("nbt") || data.get("nbt").getAsString().isBlank()) return true;
        try {
            CompoundTag expected = TagParser.parseTag(data.get("nbt").getAsString());
            CompoundTag saved = (CompoundTag) stack.save(Minecraft.getInstance().level.registryAccess());
            return contains(saved, expected);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean contains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.getAllKeys()) {
            if (!actual.contains(key)) return false;
            Tag a = actual.get(key), e = expected.get(key);
            if (a instanceof CompoundTag ac && e instanceof CompoundTag ec) { if (!contains(ac, ec)) return false; }
            else if (!a.equals(e)) return false;
        }
        return true;
    }

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        Class<?> type = screen.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Slot.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof Slot slot) return slot;
                } catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
