package com.kuzhi.itemget.client;

import com.kuzhi.itemget.ItemGet;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomSoundLibrary {
    public static final String NAMESPACE = "item_get_custom";
    private static final Map<String, Path> FILES = new LinkedHashMap<>();
    private CustomSoundLibrary() {}

    public static List<String> refresh() {
        FILES.clear(); Path folder = folder();
        try {
            Files.createDirectories(folder); Path help = folder.resolve("README.txt");
            if (!Files.exists(help)) Files.writeString(help, "Put .ogg or .mp3 files here. They appear in the Item Get! custom sound filter.\n", StandardCharsets.UTF_8);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile).filter(CustomSoundLibrary::supported).sorted(Comparator.comparing(Path::toString)).forEach(file -> {
                    String safe = file.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
                    FILES.put(NAMESPACE + ":" + safe, file);
                });
            }
        } catch (IOException ignored) {}
        return new ArrayList<>(FILES.keySet());
    }

    public static Path resolve(String id) { Path result = FILES.get(id); if (result == null) { refresh(); result = FILES.get(id); } return result; }
    public static boolean isCustom(String id) { return id != null && id.startsWith(NAMESPACE + ":"); }
    private static boolean supported(Path file) { String name = file.getFileName().toString().toLowerCase(Locale.ROOT); return name.endsWith(".ogg") || name.endsWith(".mp3"); }
    private static Path folder() { return FMLPaths.CONFIGDIR.get().resolve(ItemGet.MOD_ID).resolve("sounds"); }
}
