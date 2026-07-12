package com.kuzhi.itemget.client;

import com.kuzhi.itemget.client.screen.ManagerScreen;
import com.kuzhi.itemget.client.screen.ReminderScreen;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Arrays;

public final class ClientHooks {
    private static final Queue<ReminderRule> PENDING = new ArrayDeque<>();
    private static List<String> BIOMES = List.of(), STRUCTURES = List.of();
    public static void openManager(List<ReminderRule> rules, boolean editable, String biomes, String structures) {
        BIOMES = lines(biomes); STRUCTURES = lines(structures);
        Minecraft.getInstance().setScreen(new ManagerScreen(rules, editable));
    }
    private static List<String> lines(String value){return value==null||value.isBlank()?List.of():Arrays.stream(value.split("\n")).filter(s->!s.isBlank()).toList();}
    public static List<String> biomes(){return BIOMES;}public static List<String> structures(){return STRUCTURES;}
    public static void show(ReminderRule rule) { PENDING.add(rule); tick(); }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!PENDING.isEmpty() && mc.player != null && mc.screen == null) mc.setScreen(new ReminderScreen(PENDING.remove(), null));
    }
}
