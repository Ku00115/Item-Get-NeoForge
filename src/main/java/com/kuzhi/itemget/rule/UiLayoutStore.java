package com.kuzhi.itemget.rule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class UiLayoutStore extends SavedData {
    private static final String DATA_NAME = "item_get_ui_layout";
    private int inventoryButtonX = -1;
    private int inventoryButtonY = -1;

    public static UiLayoutStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(UiLayoutStore::new, UiLayoutStore::load), DATA_NAME);
    }

    public static UiLayoutStore load(CompoundTag tag, HolderLookup.Provider provider) {
        UiLayoutStore store = new UiLayoutStore();
        store.inventoryButtonX = tag.contains("inventoryButtonX") ? tag.getInt("inventoryButtonX") : -1;
        store.inventoryButtonY = tag.contains("inventoryButtonY") ? tag.getInt("inventoryButtonY") : -1;
        return store;
    }

    public int inventoryButtonX() { return inventoryButtonX; }
    public int inventoryButtonY() { return inventoryButtonY; }

    public void setInventoryButton(int x, int y) {
        inventoryButtonX = x;
        inventoryButtonY = y;
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("inventoryButtonX", inventoryButtonX);
        tag.putInt("inventoryButtonY", inventoryButtonY);
        return tag;
    }
}
