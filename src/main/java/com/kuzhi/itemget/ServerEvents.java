package com.kuzhi.itemget;

import com.kuzhi.itemget.network.ItemGetNetwork;
import com.kuzhi.itemget.network.RuleJson;
import com.kuzhi.itemget.network.ShowReminderPacket;
import com.kuzhi.itemget.network.SyncObserverRulesPacket;
import com.kuzhi.itemget.api.ItemGetApi;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleConditions;
import com.kuzhi.itemget.rule.RuleStore;
import com.kuzhi.itemget.rule.TriggerType;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class ServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA = "item_get_data";
    private static final int[][] STRUCTURE_SAMPLE_OFFSETS={{0,0,0},{8,0,0},{-8,0,0},{0,0,8},{0,0,-8},{16,0,0},{-16,0,0},{0,0,16},{0,0,-16},{12,0,12},{12,0,-12},{-12,0,12},{-12,0,-12},{0,8,0},{0,-8,0}};
    private static final DynamicCommandExceptionType UNKNOWN_RULE = new DynamicCommandExceptionType(id -> Component.translatable("item_get.command.progress.unknown_rule", id));

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("itemget")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> clearProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all")
                                        .executes(ctx -> grantAllProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))
                                .then(Commands.argument("rule", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ruleIds(ctx.getSource()), builder))
                                        .executes(ctx -> grantProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "rule"))))))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("rule", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ruleIds(ctx.getSource()), builder))
                                        .executes(ctx -> triggerRule(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "rule"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> triggerRule(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "rule"), true))))))
                .then(Commands.literal("show")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("rule", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ruleIds(ctx.getSource()), builder))
                                        .executes(ctx -> showRule(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "rule"))))))
                .then(Commands.literal("fire")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("manual", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(manualTriggerIds(ctx.getSource()), builder))
                                        .executes(ctx -> fireManual(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "manual"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> fireManual(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "manual"), true)))))));
    }

    private static List<String> ruleIds(CommandSourceStack source) {
        return RuleStore.get(source.getLevel()).rules().stream().map(rule -> rule.id).toList();
    }

    private static List<String> manualTriggerIds(CommandSourceStack source) {
        return RuleStore.get(source.getLevel()).rules().stream()
                .flatMap(rule -> RuleConditions.entries(rule).stream())
                .filter(condition -> condition.type() == TriggerType.MANUAL)
                .map(condition -> text(condition, "manual", ""))
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    }

    private static int addLinkTestRules(CommandSourceStack source) {
        RuleStore store = RuleStore.get(source.getLevel());
        List<String> testIds = new java.util.ArrayList<>();
        int added = 0;
        if (ModList.get().isLoaded("ponderer")) {
            ReminderRule full = linkTestRule("item_get_test_ponderer_full", "minecraft:diamond", "item_get:test_diamond_intro", "item_get.test.ponderer.title", "item_get.test.ponderer.description", "minecraft:diamond", "HORIZONTAL", "item_get:item_acquired_rare");
            ReminderRule side = linkTestRule("item_get_test_ponderer_side", "minecraft:emerald", "item_get:test_diamond_intro", "item_get.test.ponderer_side.title", "item_get.test.ponderer_side.description", "minecraft:emerald", "SIDE", "item_get:item_acquired_soft");
            added += upsertTestRule(store, full); testIds.add(full.id);
            added += upsertTestRule(store, side); testIds.add(side.id);
        }
        if (ModList.get().isLoaded("create")) {
            ReminderRule full = linkTestRule("item_get_test_create_depot", "create:depot", "create:depot", "item_get.test.create.title", "item_get.test.create.description", "create:depot", "HORIZONTAL", "item_get:item_acquired_mechanical");
            ReminderRule side = linkTestRule("item_get_test_create_side", "create:shaft", "create:shaft", "item_get.test.create_side.title", "item_get.test.create_side.description", "create:shaft", "SIDE", "item_get:item_acquired_arcade");
            added += upsertTestRule(store, full); testIds.add(full.id);
            added += upsertTestRule(store, side); testIds.add(side.id);
        }
        store.replace(List.copyOf(store.rules()));
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            resetProgress(player, testIds);
            syncObserverRules(player);
        }
        int addedCount = added;
        source.sendSuccess(() -> Component.translatable("item_get.command.test.links", addedCount), true);
        return added;
    }

    private static int upsertTestRule(RuleStore store, ReminderRule rule) {
        List<ReminderRule> rules = store.rules();
        for (int i = 0; i < rules.size(); i++) {
            if (rule.id.equals(rules.get(i).id)) {
                rules.set(i, rule);
                return 0;
            }
        }
        rules.add(rule);
        return 1;
    }

    private static ReminderRule linkTestRule(String id, String item, String ponderTarget, String title, String description, String icon, String display, String sound) {
        ReminderRule rule = new ReminderRule();
        rule.id = id;
        rule.triggerType = TriggerType.ITEM_ACQUIRED.name();
        rule.trigger.addProperty("item", item);
        rule.trigger.addProperty("count", 1);
        rule.title = title;
        rule.description = description;
        rule.icon = icon;
        rule.iconStack = "{Count:1b,id:\"" + icon + "\"}";
        rule.ponderTarget = ponderTarget;
        rule.displayStyle = display;
        rule.sound = sound;
        rule.pauseSingleplayer = true;
        rule.enabled = true;
        rule.triggerRevision = 20260721;
        return rule;
    }

    private static int showTestRule(CommandSourceStack source, Collection<ServerPlayer> targets, String ruleId) throws CommandSyntaxException {
        ReminderRule rule = findRule(source, ruleId);
        for (ServerPlayer player : targets) PacketDistributor.sendToPlayer(player, new ShowReminderPacket(rule));
        source.sendSuccess(() -> Component.translatable("item_get.command.test.show", ruleId, targets.size()), true);
        return targets.size();
    }

    private static int triggerRule(CommandSourceStack source, Collection<ServerPlayer> targets, String ruleId, boolean force) throws CommandSyntaxException {
        findRule(source, ruleId);
        int changed = 0;
        for (ServerPlayer player : targets) if (ItemGetApi.trigger(player, ruleId, force)) changed++;
        int changedCount = changed;
        source.sendSuccess(() -> Component.translatable(force ? "item_get.command.trigger.force" : "item_get.command.trigger", ruleId, changedCount, targets.size()), true);
        return changed;
    }

    private static int showRule(CommandSourceStack source, Collection<ServerPlayer> targets, String ruleId) throws CommandSyntaxException {
        findRule(source, ruleId);
        int shown = 0;
        for (ServerPlayer player : targets) if (ItemGetApi.show(player, ruleId)) shown++;
        int shownCount = shown;
        source.sendSuccess(() -> Component.translatable("item_get.command.show", ruleId, shownCount), true);
        return shown;
    }

    private static int fireManual(CommandSourceStack source, Collection<ServerPlayer> targets, String triggerId, boolean force) {
        int fired = 0;
        for (ServerPlayer player : targets) fired += ItemGetApi.fireExternalTrigger(player, triggerId, force);
        int firedCount = fired;
        source.sendSuccess(() -> Component.translatable(force ? "item_get.command.fire.force" : "item_get.command.fire", triggerId, firedCount, targets.size()), true);
        return fired;
    }

    private static int clearProgress(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) ItemGetApi.reset(player);
        source.sendSuccess(() -> Component.translatable("item_get.command.progress.clear", targets.size()), true);
        return targets.size();
    }

    private static int grantProgress(CommandSourceStack source, Collection<ServerPlayer> targets, String ruleId) throws CommandSyntaxException {
        ReminderRule rule = findRule(source, ruleId);
        for (ServerPlayer player : targets) ItemGetApi.unlock(player, rule.id);
        source.sendSuccess(() -> Component.translatable("item_get.command.progress.grant", ruleId, targets.size()), true);
        return targets.size();
    }

    private static int grantAllProgress(CommandSourceStack source, Collection<ServerPlayer> targets) {
        List<ReminderRule> rules = RuleStore.get(source.getLevel()).rules();
        for (ServerPlayer player : targets) for (ReminderRule rule : rules) ItemGetApi.unlock(player, rule.id);
        source.sendSuccess(() -> Component.translatable("item_get.command.progress.grant_all", rules.size(), targets.size()), true);
        return targets.size();
    }

    private static int revokeProgress(CommandSourceStack source, Collection<ServerPlayer> targets, String ruleId) throws CommandSyntaxException {
        findRule(source, ruleId);
        for (ServerPlayer player : targets) revokeRule(player, ruleId);
        source.sendSuccess(() -> Component.translatable("item_get.command.progress.revoke", ruleId, targets.size()), true);
        return targets.size();
    }

    private static ReminderRule findRule(CommandSourceStack source, String ruleId) throws CommandSyntaxException {
        for (ReminderRule rule : RuleStore.get(source.getLevel()).rules()) if (rule.id.equals(ruleId)) return rule;
        throw UNKNOWN_RULE.create(ruleId);
    }

    private static void grantRule(ServerPlayer player, ReminderRule rule) {
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

    private static void revokeRule(ServerPlayer player, String ruleId) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        shown.remove(ruleId);
        totals.remove(ruleId);
        states.remove(ruleId);
        revisions.remove(ruleId);
        data.put("shown", shown);
        data.put("totals", totals);
        data.put("condition_states", states);
        data.put("rule_revisions", revisions);
        removeHistory(data, ruleId);
        player.getPersistentData().put(DATA, data);
    }

    private static void resetProgress(ServerPlayer player, Collection<String> ruleIds) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag shown = data.getCompound("shown");
        CompoundTag totals = data.getCompound("totals");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (String ruleId : ruleIds) {
            shown.remove(ruleId);
            totals.remove(ruleId);
            states.remove(ruleId);
            revisions.remove(ruleId);
            removeHistory(data, ruleId);
        }
        data.put("shown", shown);
        data.put("totals", totals);
        data.put("condition_states", states);
        data.put("rule_revisions", revisions);
        player.getPersistentData().put(DATA, data);
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

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncObserverRules(player);
    }

    private static void syncObserverRules(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncObserverRulesPacket(RuleJson.write(RuleStore.get(player.serverLevel()).rules())));
    }

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
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                TriggerType type = condition.type();
                boolean hit = false;
                if (type == TriggerType.ITEM_ACQUIRED) {
                    String item = inventoryKey(rule, condition); int currentCount = hasNbt(condition) ? matchingInventory(player, condition) : current.getOrDefault(item, 0);
                    int netGain = inventoryInitialized ? Math.max(0, currentCount - previous.getInt(item)) : 0;
                    int gained = Math.max(0, netGain - credited.getInt(item));
                    if (gained > 0) totals.putInt(conditionKey(rule, condition), totals.getInt(conditionKey(rule, condition)) + gained);
                    hit = totals.getInt(conditionKey(rule, condition)) >= threshold(condition);
                } else if (type == TriggerType.ENTER_BIOME || type == TriggerType.ENTER_STRUCTURE) {
                    boolean inside = type == TriggerType.ENTER_BIOME ? biomeMatches(player, condition) : inStructure(player, text(condition, "structure", "minecraft:village_plains"));
                    String key = conditionKey(rule, condition) + "#inside";
                    boolean initialized = conditionStates.contains(key), wasInside = conditionStates.getBoolean(key); conditionStates.putBoolean(key, inside);
                    hit = initialized && inside && !wasInside;
                } else if (type != TriggerType.ENTITY_KILLED && type != TriggerType.DEATH_BY && type != TriggerType.ADVANCEMENT_DONE && stateMatches(player, condition)) hit = true;
                if (hit && conditionsSatisfied(player, rule, condition, totals)) { fire(player, rule, data, shown); break; }
            }
        }
        CompoundTag snapshot = new CompoundTag();
        current.forEach(snapshot::putInt);
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) for (RuleConditions.Entry condition : RuleConditions.entries(rule)) if (condition.type() == TriggerType.ITEM_ACQUIRED && hasNbt(condition)) snapshot.putInt(inventoryKey(rule, condition), matchingInventory(player, condition));
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

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recordDimensionSwitch(player, event.getTo().location().toString());
    }

    private static void recordGain(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return; ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()); if (itemId == null) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA); CompoundTag totals = data.getCompound("totals"), shown = data.getCompound("shown"), credited = data.getCompound("credited_gains"), states = data.getCompound("condition_states"), revisions = data.getCompound("rule_revisions");
        String id = itemId.toString(); int amount = stack.getCount(); credited.putInt(id, credited.getInt(id) + amount);
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (!rule.enabled || condition.type() != TriggerType.ITEM_ACQUIRED || !stackMatches(player, condition, stack, id)) continue;
                if (hasNbt(condition)) credited.putInt(inventoryKey(rule, condition), credited.getInt(inventoryKey(rule, condition)) + amount);
                String key = conditionKey(rule, condition);
                totals.putInt(key, totals.getInt(key) + amount);
                if (totals.getInt(key) >= threshold(condition) && conditionsSatisfied(player, rule, condition, totals)) { fire(player, rule, data, shown); break; }
            }
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

    private static boolean conditionsSatisfied(ServerPlayer player, ReminderRule rule, RuleConditions.Entry hit, CompoundTag totals) {
        List<RuleConditions.Entry> entries = RuleConditions.entries(rule);
        boolean any = "OR".equals(RuleConditions.logic(rule));
        if (any) return true;
        for (RuleConditions.Entry condition : entries) {
            boolean value = condition.index() == hit.index() ? true : conditionCurrentlyTrue(player, rule, condition, totals);
            if (!value) return false;
        }
        return true;
    }

    private static boolean conditionCurrentlyTrue(ServerPlayer player, ReminderRule rule, RuleConditions.Entry condition, CompoundTag totals) {
        boolean value = switch (condition.type()) {
            case ITEM_ACQUIRED, ENTITY_KILLED, OBSERVE_BLOCK, OBSERVE_ENTITY, HOVER_ITEM -> totals.getInt(conditionKey(rule, condition)) >= threshold(condition);
            case ENTER_BIOME -> biomeMatches(player, condition);
            case ENTER_STRUCTURE -> inStructure(player, text(condition, "structure", "minecraft:village_plains"));
            case DIMENSION_CHANGED -> dimensionMatches(player, condition);
            case DEATH_BY, ADVANCEMENT_DONE, MANUAL -> false;
            default -> stateMatches(player, condition);
        };
        return value;
    }

    private static boolean stateMatches(ServerPlayer player, RuleConditions.Entry condition) {
        return switch (condition.type()) {
            case HEALTH_AT -> player.getHealth() <= decimal(condition, "value", 10.0);
            case HUNGER_AT -> player.getFoodData().getFoodLevel() <= decimal(condition, "value", 10.0);
            case EFFECT_GAINED -> {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(text(condition, "effect", "minecraft:speed")));
                yield effect != null && player.getActiveEffects().stream().anyMatch(instance -> instance.getEffect().value() == effect);
            }
            case WEATHER_IS -> switch (text(condition, "weather", "clear")) { case "thunder" -> player.serverLevel().isThundering(); case "rain" -> player.serverLevel().isRaining() && !player.serverLevel().isThundering(); default -> !player.serverLevel().isRaining(); };
            case TIME_IS -> timeMatches(player.serverLevel().getDayTime() % 24000L, text(condition, "time", "day"));
            case ENTER_BIOME, ENTER_STRUCTURE, DIMENSION_CHANGED, DEATH_BY, ADVANCEMENT_DONE, OBSERVE_BLOCK, OBSERVE_ENTITY, HOVER_ITEM, MANUAL -> false;
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
    private static boolean biomeMatches(ServerPlayer player,RuleConditions.Entry condition){return player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(k->k.location().toString().equals(text(condition,"biome","minecraft:plains"))).orElse(false);}
    private static boolean dimensionMatches(ServerPlayer player, RuleConditions.Entry condition) { return player.serverLevel().dimension().location().toString().equals(text(condition, "dimension", "minecraft:overworld")); }
    private static String text(ReminderRule rule, String key, String fallback) { return rule.trigger.has(key) ? rule.trigger.get(key).getAsString() : fallback; }
    private static String text(RuleConditions.Entry condition, String key, String fallback) { JsonObject data = condition.data(); return data.has(key) ? data.get(key).getAsString() : fallback; }
    private static double decimal(ReminderRule rule, String key, double fallback) { try { return rule.trigger.has(key) ? rule.trigger.get(key).getAsDouble() : fallback; } catch (Exception ignored) { return fallback; } }
    private static double decimal(RuleConditions.Entry condition, String key, double fallback) { try { return condition.data().has(key) ? condition.data().get(key).getAsDouble() : fallback; } catch (Exception ignored) { return fallback; } }
    private static int threshold(RuleConditions.Entry condition) { try { return Math.max(1, condition.data().has("count") ? condition.data().get("count").getAsInt() : 1); } catch (Exception ignored) { return 1; } }
    private static boolean hasNbt(RuleConditions.Entry condition) { return condition.data().has("nbt") && !condition.data().get("nbt").getAsString().isBlank(); }
    private static String conditionKey(ReminderRule rule, RuleConditions.Entry condition) { return RuleConditions.entries(rule).size() == 1 ? rule.id : rule.id + "#c" + condition.index(); }
    private static String inventoryKey(ReminderRule rule, RuleConditions.Entry condition) { return hasNbt(condition) ? conditionKey(rule, condition) + "#nbt" : text(condition, "item", "minecraft:air"); }
    private static int matchingInventory(ServerPlayer player, RuleConditions.Entry condition) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!stack.isEmpty() && id != null && stackMatches(player, condition, stack, id.toString())) count += stack.getCount();
        }
        return count;
    }
    private static boolean stackMatches(ServerPlayer player, RuleConditions.Entry condition, ItemStack stack, String itemId) {
        if (!text(condition, "item", "minecraft:air").equals(itemId)) return false;
        if (!hasNbt(condition)) return true;
        try {
            CompoundTag expected = TagParser.parseTag(condition.data().get("nbt").getAsString());
            CompoundTag saved = (CompoundTag) stack.save(player.registryAccess());
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
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (!rule.enabled || condition.type() != TriggerType.ENTITY_KILLED) continue;
                String target = text(condition, "entity", "minecraft:pig");
                if (!target.equals(killedId.toString())) continue;
                String key = conditionKey(rule, condition);
                totals.putInt(key, totals.getInt(key) + 1);
                if (totals.getInt(key) >= threshold(condition) && conditionsSatisfied(player, rule, condition, totals)) {
                    fire(player, rule, data, shown);
                    break;
                }
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
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (!rule.enabled || condition.type() != TriggerType.DEATH_BY) continue;
                if (!text(condition, "death", "minecraft:fall").equals(deathType)) continue;
                if (conditionsSatisfied(player, rule, condition, totals)) { fire(player, rule, data, shown); break; }
            }
        }
        data.put("totals", totals); data.put("shown", shown); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    private static void recordDimensionSwitch(ServerPlayer player, String dimension) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (!rule.enabled || condition.type() != TriggerType.DIMENSION_CHANGED) continue;
                if (!text(condition, "dimension", "minecraft:overworld").equals(dimension)) continue;
                if (conditionsSatisfied(player, rule, condition, totals)) { fire(player, rule, data, shown); break; }
            }
        }
        data.put("totals", totals); data.put("shown", shown); data.put("condition_states", states); data.put("rule_revisions", revisions); player.getPersistentData().put(DATA, data);
    }

    public static void triggerObserved(ServerPlayer player, String ruleId) {
        if (player == null || ruleId == null || ruleId.isBlank()) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        CompoundTag totals = data.getCompound("totals");
        CompoundTag shown = data.getCompound("shown");
        CompoundTag states = data.getCompound("condition_states");
        CompoundTag revisions = data.getCompound("rule_revisions");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (!rule.enabled || !rule.id.equals(ruleId)) continue;
            prepareRevision(rule, shown, totals, states, revisions);
            if (!hasShown(rule, shown)) {
                RuleConditions.Entry hit = RuleConditions.entries(rule).stream().filter(c -> c.type() == TriggerType.OBSERVE_BLOCK || c.type() == TriggerType.OBSERVE_ENTITY || c.type() == TriggerType.HOVER_ITEM).findFirst().orElse(RuleConditions.entries(rule).get(0));
                totals.putInt(conditionKey(rule, hit), threshold(hit));
                if (conditionsSatisfied(player, rule, hit, totals)) fire(player, rule, data, shown);
            }
            break;
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
            prepareRevision(rule, shown, totals, states, revisions); if (hasShown(rule, shown)) continue;
            for (RuleConditions.Entry condition : RuleConditions.entries(rule)) {
                if (!rule.enabled || condition.type() != TriggerType.ADVANCEMENT_DONE) continue;
                if (!text(condition, "advancement", "minecraft:story/mine_diamond").equals(advancement)) continue;
                if (conditionsSatisfied(player, rule, condition, totals)) { fire(player, rule, data, shown); break; }
            }
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
