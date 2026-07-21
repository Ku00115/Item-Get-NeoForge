package com.kuzhi.itemget.client;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.screen.ManagerScreen;
import com.kuzhi.itemget.rule.ReminderRule;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class ConfigIconLibrary {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("item_get").resolve("images");
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    private ConfigIconLibrary() {}

    public static String label(ReminderRule rule) {
        String image = imageId(rule);
        if (image != null) return net.minecraft.network.chat.Component.translatable("item_get.editor.image", image).getString();
        return rule.icon == null || rule.icon.isBlank() ? net.minecraft.network.chat.Component.translatable("item_get.editor.auto").getString() : ManagerScreen.displayStack(rule).getHoverName().getString();
    }

    public static boolean render(GuiGraphics g, ReminderRule rule, int centerX, int centerY, float scale) {
        String image = imageId(rule);
        return render(g, image, centerX, centerY, scale);
    }

    public static boolean render(GuiGraphics g, String image, int centerX, int centerY, float scale) {
        if (image == null || image.isBlank()) return false;
        ResourceLocation texture = texture(image.trim());
        if (texture == null) return false;
        int size = Math.max(8, Math.round(16F * scale));
        int left = centerX - size / 2, top = centerY - size / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture, left, top, 0, 0, size, size, 64, 64);
        RenderSystem.disableBlend();
        return true;
    }

    public static List<String> refresh() {
        ensureDir();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(DIR)) {
            stream.filter(Files::isRegularFile)
                    .filter(ConfigIconLibrary::supported)
                    .map(DIR::relativize)
                    .map(ConfigIconLibrary::toId)
                    .sorted(Comparator.naturalOrder())
                    .forEach(out::add);
        } catch (Exception exception) {
            LOGGER.warn("Item Get! could not scan config icon directory {}", DIR, exception);
        }
        return out;
    }

    private static ResourceLocation texture(String id) {
        return CACHE.computeIfAbsent(id, ConfigIconLibrary::loadTexture);
    }

    private static ResourceLocation loadTexture(String id) {
        Path file = resolve(id);
        if (file == null) return null;
        try {
            BufferedImage source = ImageIO.read(file.toFile());
            if (source == null) return null;
            BufferedImage square = square(source);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(square, "png", bytes);
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(bytes.toByteArray()));
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "config_icons/" + Integer.toHexString(id.toLowerCase(Locale.ROOT).hashCode()));
            Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(nativeImage));
            return textureId;
        } catch (Exception exception) {
            LOGGER.warn("Item Get! could not load config icon {} from {}", id, file, exception);
            return null;
        }
    }

    private static BufferedImage square(BufferedImage source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        BufferedImage cropped = source.getSubimage(x, y, size, size);
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, 64, 64, null);
        g.dispose();
        return out;
    }

    private static Path resolve(String id) {
        String normalized = normalize(id);
        for (String ext : EXTENSIONS) {
            Path candidate = DIR.resolve(normalized + ext);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        Path candidate = DIR.resolve(normalized);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static boolean supported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (name.endsWith(ext)) return true;
        return false;
    }

    private static String toId(Path path) {
        String value = path.toString().replace('\\', '/');
        for (String ext : EXTENSIONS) if (value.toLowerCase(Locale.ROOT).endsWith(ext)) return value.substring(0, value.length() - ext.length()).replace('\\', '/');
        return value.replace('\\', '/');
    }

    private static String normalize(String id) { return id.replace('\\', '/').replace(':', '/'); }
    private static String imageId(ReminderRule rule) { return rule.iconImage == null || rule.iconImage.isBlank() ? null : rule.iconImage.trim(); }
    private static void ensureDir() { try { Files.createDirectories(DIR); } catch (Exception ignored) {} }
}
