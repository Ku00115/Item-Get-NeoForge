package com.kuzhi.itemget.rule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class RuleStore extends SavedData {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type LIST = new TypeToken<ArrayList<ReminderRule>>() {}.getType();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<ReminderRule> rules = new ArrayList<>();

    public static RuleStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RuleStore::new, RuleStore::load), "item_get_rules");
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

    public List<ReminderRule> rules() { return rules; }
    public void replace(List<ReminderRule> updated) { rules.clear(); rules.addAll(updated); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { tag.putString("rules", GSON.toJson(rules)); return tag; }
}
