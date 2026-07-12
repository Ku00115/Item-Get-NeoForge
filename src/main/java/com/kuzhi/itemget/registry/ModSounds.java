package com.kuzhi.itemget.registry;

import com.kuzhi.itemget.ItemGet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ItemGet.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED = SOUNDS.register("item_acquired",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "item_acquired")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_SOFT = register("item_acquired_soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_RARE = register("item_acquired_rare");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_MYSTIC = register("item_acquired_mystic");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_MECHANICAL = register("item_acquired_mechanical");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_ARCADE = register("item_acquired_arcade");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ACQUIRED_RELIC = register("item_acquired_relic");
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, name))); }
    private ModSounds() {}
}
