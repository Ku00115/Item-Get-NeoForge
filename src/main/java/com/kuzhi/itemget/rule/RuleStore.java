package com.kuzhi.itemget.rule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.loading.FMLPaths;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class RuleStore extends SavedData {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type LIST = new TypeToken<ArrayList<ReminderRule>>() {}.getType();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEGACY_WORLD_DATA = "item_get_rules";
    private static RuleStore global;
    private final List<ReminderRule> rules = new ArrayList<>();

    public static synchronized RuleStore get(ServerLevel level) {
        RuleStore store = global();
        if (store.rules.isEmpty()) {
            RuleStore legacy = legacyWorldStore(level);
            if (!legacy.rules.isEmpty()) {
                store.rules.addAll(legacy.rules);
                store.saveGlobal();
                LOGGER.info("Migrated {} Item Get! reminder rule(s) from world data to global config {}", store.rules.size(), rulesFile());
            }
        }
        return store;
    }

    private static RuleStore legacyWorldStore(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RuleStore::new, RuleStore::load), LEGACY_WORLD_DATA);
    }

    private static RuleStore global() {
        if (global == null) global = loadGlobal();
        return global;
    }

    public static RuleStore load(CompoundTag tag, HolderLookup.Provider provider) {
        RuleStore store = new RuleStore();
        try {
            List<ReminderRule> loaded = GSON.fromJson(tag.getString("rules"), LIST);
            if (loaded != null) loaded.stream().filter(java.util.Objects::nonNull).forEach(store.rules::add);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not read Item Get! reminder rules; keeping the world loadable", exception);
        }
        return store;
    }

    private static RuleStore loadGlobal() {
        RuleStore store = new RuleStore();
        Path file = rulesFile();
        if (!Files.exists(file)) return store;
        try {
            List<ReminderRule> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
            if (loaded != null) loaded.stream().filter(java.util.Objects::nonNull).forEach(store.rules::add);
        } catch (Exception exception) {
            LOGGER.error("Could not read Item Get! global reminder rules from {}; starting with an empty rule list", file, exception);
        }
        return store;
    }

    private static Path rulesFile() {
        return FMLPaths.CONFIGDIR.get().resolve("item_get").resolve("rules.json");
    }

    public List<ReminderRule> rules() { return rules; }
    public void replace(List<ReminderRule> updated) {
        rules.clear();
        if (updated != null) updated.stream().filter(java.util.Objects::nonNull).forEach(rules::add);
        saveGlobal();
        setDirty();
    }

    private void saveGlobal() {
        Path file = rulesFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(rules), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception exception) {
            LOGGER.error("Could not save Item Get! global reminder rules to {}", file, exception);
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { tag.putString("rules", GSON.toJson(rules)); return tag; }
}
