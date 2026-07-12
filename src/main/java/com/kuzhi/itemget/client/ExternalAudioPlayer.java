package com.kuzhi.itemget.client;

import net.minecraft.client.sounds.JOrbisAudioStream;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

final class ExternalAudioPlayer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Playing> ACTIVE = new CopyOnWriteArrayList<>();
    private static SoundEngineExecutor executor;
    private ExternalAudioPlayer() {}

    static boolean play(Path file) {
        if (file == null || !Files.isRegularFile(file)) return false;
        SoundEngineExecutor soundThread = executor(); if (soundThread == null) return false;
        CompletableFuture.runAsync(() -> {
            try { Decoded decoded = decode(file); soundThread.execute(() -> start(decoded)); }
            catch (Exception e) { LOGGER.warn("Item Get! failed to decode custom sound {}", file, e); }
        });
        return true;
    }

    static void tick() {
        SoundEngineExecutor soundThread = executor(); if (soundThread == null || ACTIVE.isEmpty()) return;
        soundThread.execute(() -> {
            for (Playing playing : ACTIVE) if (AL10.alGetSourcei(playing.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourceStop(playing.source); AL10.alDeleteSources(playing.source); AL10.alDeleteBuffers(playing.buffer); ACTIVE.remove(playing);
            }
        });
    }

    private static Decoded decode(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try (AudioStream stream = name.endsWith(".mp3") ? new Mp3AudioStream(Files.newInputStream(file)) : new JOrbisAudioStream(Files.newInputStream(file))) {
            AudioFormat format = stream.getFormat(); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (bytes.size() < 64 * 1024 * 1024) { ByteBuffer part = stream.read(65536); if (part == null || !part.hasRemaining()) break; byte[] chunk = new byte[part.remaining()]; part.get(chunk); bytes.write(chunk); }
            ByteBuffer pcm = ByteBuffer.allocateDirect(bytes.size()).order(ByteOrder.LITTLE_ENDIAN); pcm.put(bytes.toByteArray()).flip(); return new Decoded(format, pcm);
        }
    }

    private static void start(Decoded decoded) {
        int buffer = AL10.alGenBuffers(); int source = AL10.alGenSources();
        AudioFormat f = decoded.format; boolean stereo = f.getChannels() > 1, sixteen = f.getSampleSizeInBits() > 8;
        int alFormat = stereo ? (sixteen ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_STEREO8) : (sixteen ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_MONO8);
        AL10.alBufferData(buffer, alFormat, decoded.pcm, Math.round(f.getSampleRate())); AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE); AL10.alSourcef(source, AL10.AL_GAIN, Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));
        AL10.alSourcePlay(source); ACTIVE.add(new Playing(source, buffer));
    }

    private static SoundEngineExecutor executor() {
        if (executor != null) return executor;
        try { Field engineField = field(SoundManager.class, SoundEngine.class); Field executorField = field(SoundEngine.class, SoundEngineExecutor.class); executor = (SoundEngineExecutor)executorField.get(engineField.get(Minecraft.getInstance().getSoundManager())); }
        catch (ReflectiveOperationException e) { LOGGER.warn("Item Get! could not access Minecraft sound executor", e); }
        return executor;
    }

    private static Field field(Class<?> owner, Class<?> type) throws NoSuchFieldException { for (Field f : owner.getDeclaredFields()) if (f.getType() == type) { f.setAccessible(true); return f; } throw new NoSuchFieldException(owner.getName()); }
    private record Decoded(AudioFormat format, ByteBuffer pcm) {}
    private record Playing(int source, int buffer) {}
}
