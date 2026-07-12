package com.kuzhi.itemget.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;

public final class AudioHelper {
    private AudioHelper() {}

    public static boolean exists(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id.trim());
        return key != null && (BuiltInRegistries.SOUND_EVENT.containsKey(key) || CustomSoundLibrary.isCustom(id));
    }

    public static boolean play(String id) {
        if (CustomSoundLibrary.isCustom(id)) return ExternalAudioPlayer.play(CustomSoundLibrary.resolve(id));
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id.trim());
        SoundEvent sound = key == null ? null : BuiltInRegistries.SOUND_EVENT.get(key);
        if (sound == null) return false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
        return true;
    }

    public static void tick() { ExternalAudioPlayer.tick(); }
}
