package com.kuzhi.itemget.api;

import com.google.gson.JsonObject;
import com.kuzhi.itemget.network.RuleJson;
import com.kuzhi.itemget.network.ShowReminderPacket;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleConditions;
import com.kuzhi.itemget.rule.RuleStore;
import com.kuzhi.itemget.rule.TriggerType;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

/** Public server-side hooks for modpacks and optional integrations. */
public final class ItemGetApi {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA = "item_get_data";
    private static final int[][] STRUCTURE_SAMPLE_OFFSETS = {{0,0,0},{8,0,0},{-8,0,0},{0,0,8},{0,0,-8},{16,0,0},{-16,0,0},{0,0,16},{0,0,-16},{12,0,12},{12,0,-12},{-12,0,12},{-12,0,-12},{0,8,0},{0,-8,0}};

    private ItemGetApi() {}

    public static boolean trigger(ServerPlayer player, String ruleId) {
        return trigger(player, ruleId, false);
    }

    public static boolean trigger(ServerPlayer player, String ruleId, boolean force) {
        ReminderRule rule = findRule(player, ruleId);
        return rule != null && trigger(player, rule, force);
    }

    public static boolean show(ServerPlayer player, String ruleId) {
        ReminderRule rule = findRule(player, ruleId);
        if (player == null || rule == null) return false;
        send(player, rule);
        return true;
    }

    public static boolean unlock(ServerPlayer player, String ruleId) {
        ReminderRule rule = findRule(player, ruleId);
        if (player == null || rule == null) return false;
        unlockRule(player, rule);
        return true;
    }

    public static boolean isUnlocked(ServerPlayer player, String ruleId) {
        ReminderRule rule = findRule(player, ruleId);
        if (player == null || rule == null) return false;
        return hasShown(rule, player.getPersistentData().getCompound(DATA).getCompound("shown"));
    }

    public static boolean reset(ServerPlayer player) {
        if (player == null) return false;
        player.getPersistentData().remove(DATA);
        return true;
    }

    public static int reset(ServerPlayer player, Collection<String> ruleIds) {
        if (player == null || ruleIds == null || ruleIds.isEmpty()) return 0;
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        int changed = 0;
        for (String ruleId : ruleIds) {
            if (ruleId == null || ruleId.isBlank()) continue;
            shown.remove(ruleId);
            totals.remove(ruleId);
            states.remove(ruleId);
            revisions.remove(ruleId);
            removeHistory(data, ruleId);
            changed++;
        }
        data.put("shown", shown);
        data.put("totals", totals);
        data.put("condition_states", states);
        data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
        return changed;
    }

    public static int fireExternalTrigger(ServerPlayer player, String triggerId) {
        return fireExternalTrigger(player, triggerId, false);
    }

    public static int fireExternalTrigger(ServerPlayer player, String triggerId, boolean force) {
        if (player == null || triggerId == null || triggerId.isBlank()) return 0;
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        int fired = 0;
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled) continue;
            prepareRevision(rule, shown, totals, states, revisions);
            if (!force && hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (condition.type() != TriggerType.MANUAL || !triggerId.equals(text(condition, "manual", ""))) continue;
                if (force || conditionsSatisfied(player, rule, condition, totals)) {
                    fire(player, rule, data, shown);
                    fired++;
                }
                break;
            }
        }
        data.put("totals", totals);
        data.put("shown", shown);
        data.put("condition_states", states);
        data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
        return fired;
    }

    public static ReminderRule findRule(ServerPlayer player, String ruleId) {
        if (player == null || ruleId == null || ruleId.isBlank()) return null;
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) if (rule.id.equals(ruleId)) return rule;
        return null;
    }

    private static boolean trigger(ServerPlayer player, ReminderRule rule, boolean force) {
        if (player == null || rule == null) return false;
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        prepareRevision(rule, shown, totals, states, revisions);
        if (!force && (!rule.enabled || hasShown(rule, shown))) return false;
        fire(player, rule, data, shown);
        data.put("shown", shown);
        data.put("totals", totals);
        data.put("condition_states", states);
        data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
        return true;
    }

    private static void unlockRule(ServerPlayer player, ReminderRule rule) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag revisions = data.getCompound("rule_revisions");
        shown.putInt(rule.id, revision(rule));
        revisions.putInt(rule.id, revision(rule));
        recordHistory(data, rule);
        data.put("shown", shown);
        data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
    }

    private static void fire(ServerPlayer player, ReminderRule rule, CompoundTag data, CompoundTag shown) {
        shown.putInt(rule.id, revision(rule));
        recordHistory(data, rule);
        send(player, rule);
        LOGGER.info("Item Get! API triggered rule {} ({}) for {}", rule.id, rule.triggerType, player.getGameProfile().getName());
    }

    private static void send(ServerPlayer player, ReminderRule rule) {
        PacketDistributor.sendToPlayer(player, new ShowReminderPacket(rule));
    }

    private static void recordHistory(CompoundTag data, ReminderRule rule) {
        ListTag history = data.getList("history", Tag.TAG_STRING);
        String marker = rule.id + "#" + revision(rule);
        for (Tag tag : history) try {
            ReminderRule old = RuleJson.GSON.fromJson(tag.getAsString(), ReminderRule.class);
            if (old != null && marker.equals(old.id + "#" + revision(old))) return;
        } catch (RuntimeException ignored) {}
        history.add(StringTag.valueOf(RuleJson.GSON.toJson(rule)));
        data.put("history", history);
    }

    private static void removeHistory(CompoundTag data, String ruleId) {
        ListTag history = data.getList("history", Tag.TAG_STRING);
        ListTag kept = new ListTag();
        for (Tag tag : history) {
            try {
                ReminderRule old = RuleJson.GSON.fromJson(tag.getAsString(), ReminderRule.class);
                if (old != null && ruleId.equals(old.id)) continue;
            } catch (RuntimeException ignored) {}
            kept.add(tag.copy());
        }
        data.put("history", kept);
    }

    private static boolean conditionsSatisfied(ServerPlayer player, ReminderRule rule, RuleConditions.Entry hit, CompoundTag totals) {
        List<RuleConditions.Entry> entries = RuleConditions.entries(rule);
        if ("OR".equals(RuleConditions.logic(rule))) return true;
        for (RuleConditions.Entry condition : entries) {
            boolean value = condition.index() == hit.index() || conditionCurrentlyTrue(player, rule, condition, totals);
            if (!value) return false;
        }
        return true;
    }

    private static boolean conditionCurrentlyTrue(ServerPlayer player, ReminderRule rule, RuleConditions.Entry condition, CompoundTag totals) {
        return switch (condition.type()) {
            case ITEM_ACQUIRED, ENTITY_KILLED, OBSERVE_BLOCK, OBSERVE_ENTITY, HOVER_ITEM -> totals.getInt(conditionKey(rule, condition)) >= threshold(condition);
            case ENTER_BIOME -> biomeMatches(player, condition);
            case ENTER_STRUCTURE -> inStructure(player, text(condition, "structure", "minecraft:village_plains"));
            case DIMENSION_CHANGED -> dimensionMatches(player, condition);
            case DEATH_BY, ADVANCEMENT_DONE, MANUAL -> false;
            default -> stateMatches(player, condition);
        };
    }

    private static boolean stateMatches(ServerPlayer player, RuleConditions.Entry condition) {
        return switch (condition.type()) {
            case HEALTH_AT -> player.getHealth() <= decimal(condition, "value", 10.0);
            case HUNGER_AT -> player.getFoodData().getFoodLevel() <= decimal(condition, "value", 10.0);
            case EFFECT_GAINED -> {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(text(condition, "effect", "minecraft:speed")));
                yield effect != null && player.getActiveEffects().stream().anyMatch(instance -> instance.getEffect().value() == effect);
            }
            case WEATHER_IS -> switch (text(condition, "weather", "clear")) {
                case "thunder" -> player.serverLevel().isThundering();
                case "rain" -> player.serverLevel().isRaining() && !player.serverLevel().isThundering();
                default -> !player.serverLevel().isRaining();
            };
            case TIME_IS -> timeMatches(player.serverLevel().getDayTime() % 24000L, text(condition, "time", "day"));
            default -> false;
        };
    }

    private static boolean timeMatches(long time, String value) {
        return switch (value) {
            case "noon" -> time >= 5000 && time < 7000;
            case "night" -> time >= 12000 && time < 14000;
            case "midnight" -> time >= 17500 && time < 19000;
            default -> time < 3000 || time >= 23000;
        };
    }

    private static boolean inStructure(ServerPlayer player, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return false;
        var registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = registry.get(location);
        if (structure == null) return false;
        for (int[] offset : STRUCTURE_SAMPLE_OFFSETS) {
            var start = player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition().offset(offset[0], offset[1], offset[2]), structure);
            if (start != null && start.isValid()) return true;
        }
        return false;
    }

    private static boolean biomeMatches(ServerPlayer player, RuleConditions.Entry condition) {
        return player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().toString().equals(text(condition, "biome", "minecraft:plains"))).orElse(false);
    }

    private static boolean dimensionMatches(ServerPlayer player, RuleConditions.Entry condition) {
        return player.serverLevel().dimension().location().toString().equals(text(condition, "dimension", "minecraft:overworld"));
    }

    private static String text(RuleConditions.Entry condition, String key, String fallback) {
        JsonObject data = condition.data();
        return data.has(key) ? data.get(key).getAsString() : fallback;
    }

    private static double decimal(RuleConditions.Entry condition, String key, double fallback) {
        try { return condition.data().has(key) ? condition.data().get(key).getAsDouble() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static int threshold(RuleConditions.Entry condition) {
        try { return Math.max(1, condition.data().has("count") ? condition.data().get("count").getAsInt() : 1); }
        catch (Exception ignored) { return 1; }
    }

    private static String conditionKey(ReminderRule rule, RuleConditions.Entry condition) {
        return RuleConditions.entries(rule).size() == 1 ? rule.id : rule.id + "#c" + condition.index();
    }

    private static int revision(ReminderRule rule) { return Math.max(1, rule.triggerRevision); }
    private static boolean hasShown(ReminderRule rule, CompoundTag shown) { return shown.getInt(rule.id) == revision(rule); }
    private static void prepareRevision(ReminderRule rule, CompoundTag shown, CompoundTag totals, CompoundTag states, CompoundTag revisions) {
        int current = revision(rule), known = revisions.contains(rule.id) ? revisions.getInt(rule.id) : 1;
        if (known != current) {
            shown.remove(rule.id);
            totals.remove(rule.id);
            states.remove(rule.id);
        }
        revisions.putInt(rule.id, current);
    }
}
