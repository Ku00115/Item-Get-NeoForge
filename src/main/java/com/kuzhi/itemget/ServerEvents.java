package com.kuzhi.itemget;

import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.network.RuleJson;
import com.kuzhi.itemget.network.ShowReminderPacket;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleStore;
import com.kuzhi.itemget.rule.TriggerType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.HashMap;
import java.util.Map;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class ServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA = "item_get_data";
    private static final int[][] STRUCTURE_SAMPLE_OFFSETS={{0,0,0},{8,0,0},{-8,0,0},{0,0,8},{0,0,-8},{16,0,0},{-16,0,0},{0,0,16},{0,0,-16},{12,0,12},{12,0,-12},{-12,0,12},{-12,0,-12},{0,8,0},{0,-8,0}};

    @SubscribeEvent
    public void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide || event.getEntity().tickCount % 10 != 0) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag previous = data.getCompound("inventory");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag credited = data.getCompound("credited_gains");
        CompoundTag conditionStates = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        boolean inventoryInitialized = data.contains("inventory");
        Map<String, Integer> current = inventory(player);

        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled) continue;
            prepareRevision(rule, shown, totals, conditionStates, revisions);
            if (hasShown(rule, shown)) continue;
            TriggerType type = TriggerType.parse(rule.triggerType);
            if (type == TriggerType.ITEM_ACQUIRED) {
                String item = rule.target(); int netGain = inventoryInitialized ? Math.max(0, current.getOrDefault(item, 0) - previous.getInt(item)) : 0;
                int gained = Math.max(0, netGain - credited.getInt(item));
                if (gained > 0) totals.putInt(rule.id, totals.getInt(rule.id) + gained);
                if (totals.getInt(rule.id) >= rule.threshold()) fire(player, rule, data, shown);
            } else if (type == TriggerType.ENTER_BIOME || type == TriggerType.ENTER_STRUCTURE) {
                boolean inside = type == TriggerType.ENTER_BIOME ? biomeMatches(player, rule) : inStructure(player, text(rule,"structure","minecraft:village_plains"));
                boolean initialized = conditionStates.contains(rule.id), wasInside = conditionStates.getBoolean(rule.id); conditionStates.putBoolean(rule.id, inside);
                if (initialized && inside && !wasInside) fire(player,rule,data,shown);
            } else if (type != TriggerType.ENTITY_KILLED && type != TriggerType.DEATH_BY && type != TriggerType.ADVANCEMENT_DONE && stateMatches(player, rule, type)) fire(player, rule, data, shown);
        }
        CompoundTag snapshot = new CompoundTag();
        current.forEach(snapshot::putInt);
        data.put("inventory", snapshot); data.put("totals", totals); data.put("shown", shown); data.put("credited_gains", new CompoundTag()); data.put("condition_states",conditionStates); data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
    }

    @SubscribeEvent
    public void itemPickup(ItemEntityPickupEvent.Post event) { if (event.getPlayer() instanceof ServerPlayer player) { ItemStack picked=event.getOriginalStack(); picked.setCount(Math.max(0,picked.getCount()-event.getCurrentStack().getCount())); recordGain(player,picked); } }
    @SubscribeEvent
    public void itemCrafted(PlayerEvent.ItemCraftedEvent event) { if (event.getEntity() instanceof ServerPlayer player) recordGain(player, event.getCrafting()); }
    @SubscribeEvent
    public void itemSmelted(PlayerEvent.ItemSmeltedEvent event) { if (event.getEntity() instanceof ServerPlayer player) recordGain(player, event.getSmelting()); }

    @SubscribeEvent
    public void playerClone(PlayerEvent.Clone event) {
        CompoundTag originalData = event.getOriginal().getPersistentData().getCompound(DATA);
        if (!originalData.isEmpty()) {
            event.getEntity().getPersistentData().put(DATA, originalData.copy());
            LOGGER.debug("Item Get! copied reminder progress for cloned player {} (death={})", event.getEntity().getGameProfile().getName(), event.isWasDeath());
        }
    }

    private static void recordGain(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return; ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()); if (itemId == null) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA); CompoundTag totals = data.getCompound("totals"), shown = data.getCompound("shown"), credited = data.getCompound("credited_gains"), states = data.getCompound("condition_states"), revisions = data.getCompound("rule_revisions");
        String id = itemId.toString(); int amount = stack.getCount(); credited.putInt(id, credited.getInt(id) + amount);
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled || TriggerType.parse(rule.triggerType) != TriggerType.ITEM_ACQUIRED || !rule.target().equals(id)) continue;
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            totals.putInt(rule.id, totals.getInt(rule.id) + amount); if (totals.getInt(rule.id) >= rule.threshold()) fire(player, rule, data, shown);
        }
        data.put("totals", totals); data.put("shown", shown); data.put("credited_gains", credited); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    private static void fire(ServerPlayer player, ReminderRule rule, CompoundTag data, CompoundTag shown) {
        shown.putInt(rule.id, revision(rule)); recordHistory(data, rule); PacketDistributor.sendToPlayer(player, new ShowReminderPacket(rule));
        LOGGER.info("Item Get! triggered rule {} ({}) for {}", rule.id, rule.triggerType, player.getGameProfile().getName());
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

    private static boolean stateMatches(ServerPlayer player, ReminderRule rule, TriggerType type) {
        return switch (type) {
            case HEALTH_AT -> player.getHealth() <= decimal(rule, "value", 10.0);
            case HUNGER_AT -> player.getFoodData().getFoodLevel() <= decimal(rule, "value", 10.0);
            case EFFECT_GAINED -> {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(text(rule, "effect", "minecraft:speed")));
                yield effect != null && player.getActiveEffects().stream().anyMatch(instance -> instance.getEffect().value() == effect);
            }
            case WEATHER_IS -> switch (text(rule, "weather", "clear")) { case "thunder" -> player.serverLevel().isThundering(); case "rain" -> player.serverLevel().isRaining() && !player.serverLevel().isThundering(); default -> !player.serverLevel().isRaining(); };
            case TIME_IS -> timeMatches(player.serverLevel().getDayTime() % 24000L, text(rule, "time", "day"));
            case ENTER_BIOME, ENTER_STRUCTURE, DEATH_BY, ADVANCEMENT_DONE -> false;
            default -> false;
        };
    }

    private static boolean timeMatches(long time, String value) { return switch (value) { case "noon" -> time >= 5000 && time < 7000; case "night" -> time >= 12000 && time < 14000; case "midnight" -> time >= 17500 && time < 19000; default -> time < 3000 || time >= 23000; }; }
    private static boolean inStructure(ServerPlayer player, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id); if (location == null) return false;
        var registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure=registry.get(location);if(structure==null)return false;
        for(int[] offset:STRUCTURE_SAMPLE_OFFSETS){var start=player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition().offset(offset[0],offset[1],offset[2]),structure);if(start!=null&&start.isValid())return true;}return false;
    }
    private static boolean biomeMatches(ServerPlayer player,ReminderRule rule){return player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(k->k.location().toString().equals(text(rule,"biome","minecraft:plains"))).orElse(false);}
    private static String text(ReminderRule rule, String key, String fallback) { return rule.trigger.has(key) ? rule.trigger.get(key).getAsString() : fallback; }
    private static double decimal(ReminderRule rule, String key, double fallback) { try { return rule.trigger.has(key) ? rule.trigger.get(key).getAsDouble() : fallback; } catch (Exception ignored) { return fallback; } }

    @SubscribeEvent
    public void livingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer dead) recordDeath(dead, deathTypeId(event));
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (killedId == null) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled || TriggerType.parse(rule.triggerType) != TriggerType.ENTITY_KILLED) continue;
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            String target = rule.trigger.has("entity") ? rule.trigger.get("entity").getAsString() : "minecraft:pig";
            if (!target.equals(killedId.toString())) continue;
            totals.putInt(rule.id, totals.getInt(rule.id) + 1);
            if (totals.getInt(rule.id) >= rule.threshold()) {
                fire(player, rule, data, shown);
            }
        }
        data.put("totals", totals); data.put("shown", shown); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    private static void recordDeath(ServerPlayer player, String deathType) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled || TriggerType.parse(rule.triggerType) != TriggerType.DEATH_BY) continue;
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            if (!text(rule, "death", "minecraft:fall").equals(deathType)) continue;
            fire(player, rule, data, shown);
        }
        data.put("totals", totals); data.put("shown", shown); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    private static String deathTypeId(LivingDeathEvent event) {
        return event.getSource().typeHolder().unwrapKey().map(key -> key.location().toString()).orElse(event.getSource().getMsgId());
    }

    @SubscribeEvent
    public void advancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String advancement = event.getAdvancement().id().toString();
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled || TriggerType.parse(rule.triggerType) != TriggerType.ADVANCEMENT_DONE) continue;
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            if (!text(rule, "advancement", "minecraft:story/mine_diamond").equals(advancement)) continue;
            fire(player, rule, data, shown);
        }
        data.put("totals", totals); data.put("shown", shown); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    private static int revision(ReminderRule rule) { return Math.max(1, rule.triggerRevision); }
    private static boolean hasShown(ReminderRule rule, CompoundTag shown) { return shown.getInt(rule.id) == revision(rule); }
    private static void prepareRevision(ReminderRule rule, CompoundTag shown, CompoundTag totals, CompoundTag states, CompoundTag revisions) {
        int current = revision(rule), known = revisions.contains(rule.id) ? revisions.getInt(rule.id) : 1;
        if (known != current) { shown.remove(rule.id); totals.remove(rule.id); states.remove(rule.id); }
        revisions.putInt(rule.id, current);
    }

    private static Map<String, Integer> inventory(ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!stack.isEmpty() && id != null) counts.merge(id.toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
}
