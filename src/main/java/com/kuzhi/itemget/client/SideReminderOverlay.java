package com.kuzhi.itemget.client;

import com.kuzhi.itemget.client.screen.ManagerScreen;
import com.kuzhi.itemget.client.screen.ReminderScreen;
import com.kuzhi.itemget.client.screen.TranslatedText;
import com.kuzhi.itemget.rule.ReminderRule;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SideReminderOverlay {
    private static final ResourceLocation ITEM_GLOW = ResourceLocation.fromNamespaceAndPath("item_get", "textures/gui/item_glow.png");
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final Deque<ReminderRule> PENDING = new ArrayDeque<>();
    private static final long LIFE_MS = 5200L;
    private static final long EXIT_MS = 450L;
    private static final int MAX_VISIBLE = 3;
    private static long blockedSince = -1L;

    private SideReminderOverlay() {}

    public static void push(ReminderRule rule) {
        PENDING.addLast(rule);
        admitPending(Util.getMillis());
    }

    public static void preview(ReminderRule rule) {
        long now = Util.getMillis();
        ENTRIES.addLast(new Entry(rule, now, nextSlot(), true));
        if (rule.sound != null && !rule.sound.isBlank()) AudioHelper.play(rule.sound);
    }

    public static void tick() {
        long now = Util.getMillis();
        if (isScreenBlocked()) {
            if (blockedSince < 0L) blockedSince = now;
            ENTRIES.removeIf(entry -> entry.overScreen && now - entry.started > LIFE_MS);
            compactSlots();
            return;
        }
        resumeAfterBlock(now);
        ENTRIES.removeIf(entry -> now - entry.started > LIFE_MS);
        compactSlots();
        admitPending(now);
    }

    public static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        boolean blocked = isScreenBlocked();
        if (mc.player == null || ENTRIES.isEmpty() || (blocked && !hasOverScreen())) return;
        long now = Util.getMillis();
        if (!blocked) resumeAfterBlock(now);
        ENTRIES.removeIf(entry -> now - entry.started > LIFE_MS);
        int width = mc.getWindow().getGuiScaledWidth();
        int baseY = Math.max(12, width < 360 ? 8 : 24);
        for (Entry entry : ENTRIES) {
            if (blocked && !entry.overScreen) continue;
            if (entry.exiting) continue;
            draw(g, entry.rule, now - entry.started, baseY + entry.slot * 43);
        }
        for (Entry entry : ENTRIES) {
            if (blocked && !entry.overScreen) continue;
            if (!entry.exiting) continue;
            draw(g, entry.rule, now - entry.started, baseY + entry.slot * 43);
        }
    }

    public static boolean openLatestDetail() {
        Minecraft mc = Minecraft.getInstance();
        for (var it = ENTRIES.descendingIterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.exiting) continue;
            mc.setScreen(new ReminderScreen(entry.rule, null));
            return true;
        }
        return false;
    }

    public static boolean openLatestPonder() {
        return openLatestPonderer();
    }

    public static boolean openLatestCreatePonder() {
        for (var it = ENTRIES.descendingIterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.exiting || entry.rule.ponderTarget == null || entry.rule.ponderTarget.isBlank()) continue;
            if (ClientHooks.openCreatePonder(entry.rule.ponderTarget)) return true;
        }
        return false;
    }

    public static boolean openLatestPonderer() {
        for (var it = ENTRIES.descendingIterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.exiting || entry.rule.ponderTarget == null || entry.rule.ponderTarget.isBlank()) continue;
            if (ClientHooks.openPonderer(entry.rule.ponderTarget)) return true;
        }
        return false;
    }

    private static void admitPending(long now) {
        if (isScreenBlocked()) return;
        while (!PENDING.isEmpty() && !hasExiting() && activeCount() < MAX_VISIBLE) {
            ReminderRule rule = PENDING.removeFirst();
            ENTRIES.addLast(new Entry(rule, now, nextSlot(), false));
            if (rule.sound != null && !rule.sound.isBlank()) AudioHelper.play(rule.sound);
        }
        if (!PENDING.isEmpty() && !hasExiting() && activeCount() >= MAX_VISIBLE) markOldestActiveExiting(now);
    }

    private static boolean isScreenBlocked() {
        return Minecraft.getInstance().screen != null;
    }

    private static void resumeAfterBlock(long now) {
        if (blockedSince < 0L) return;
        long blocked = Math.max(0L, now - blockedSince);
        for (Entry entry : ENTRIES) if (!entry.overScreen) entry.started += blocked;
        blockedSince = -1L;
    }

    private static int activeCount() {
        int count = 0;
        for (Entry entry : ENTRIES) if (!entry.exiting) count++;
        return count;
    }

    private static boolean hasExiting() {
        for (Entry entry : ENTRIES) if (entry.exiting) return true;
        return false;
    }

    private static boolean hasOverScreen() {
        for (Entry entry : ENTRIES) if (entry.overScreen) return true;
        return false;
    }

    private static int nextSlot() {
        int slot = 0;
        for (Entry entry : ENTRIES) if (!entry.exiting) slot = Math.max(slot, entry.slot + 1);
        return Math.min(slot, MAX_VISIBLE - 1);
    }

    private static void compactSlots() {
        if (hasExiting()) return;
        int slot = 0;
        for (Entry entry : ENTRIES) if (!entry.exiting) entry.slot = slot++;
    }

    private static void markOldestActiveExiting(long now) {
        for (Entry entry : ENTRIES) {
            if (entry.exiting) continue;
            entry.exiting = true;
            entry.started = now - (LIFE_MS - EXIT_MS);
            return;
        }
    }

    private static void draw(GuiGraphics g, ReminderRule rule, long age, int y) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int panelWidth = Math.min(154, Math.max(126, screenWidth - 24));
        int panelHeight = 38;
        float in = smooth(clamp(age / 220F));
        float out = smooth(clamp((LIFE_MS - age) / 450F));
        float alpha = in * (0.65F + 0.35F * out);
        float slide = (1F - in) * 18F + (1F - out) * (panelWidth + 14);
        int right = screenWidth - 8 + Math.round(slide);
        int left = right - panelWidth;
        int bottom = y + panelHeight;
        int a1 = (int)(154 * alpha), a2 = (int)(102 * alpha);
        g.fillGradient(left, y, right, bottom, 0, a1 << 24, a2 << 24);
        g.fill(left, y, left + 2, bottom, ((int)(210 * alpha) << 24) | 0xD8E7F5);

        int iconX = left + 15, iconY = y + 13;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha * .62F);
        g.blit(ITEM_GLOW, iconX - 14, iconY - 14, 0, 0, 28, 28, 28, 28);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        boolean image = ConfigIconLibrary.render(g, rule, iconX, iconY, .82F);
        if (!image) {
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(.82F, .82F, 1);
            g.renderItem(ManagerScreen.displayStack(rule), -8, -8);
            g.pose().popPose();
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        int textLeft = left + 29;
        int textWidth = Math.max(42, right - textLeft - 7);
        String title = TranslatedText.resolve(rule.title);
        if (title.isBlank()) title = Component.translatable("item_get.manager.unnamed").getString();
        g.drawString(mc.font, mc.font.plainSubstrByWidth(title, textWidth), textLeft, y + 4, color(0xFFE9B0, alpha), false);
        FormattedCharSequence summary = mc.font.split(ManagerScreen.displaySubtitle(rule, age), textWidth).stream().findFirst().orElse(FormattedCharSequence.EMPTY);
        g.drawString(mc.font, summary, textLeft, y + 15, color(0xDDE6EE, alpha), false);
        Component hint = Component.translatable("item_get.side.detail_hint", ClientEvents.HANDBOOK.getTranslatedKeyMessage());
        g.drawString(mc.font, mc.font.plainSubstrByWidth(hint.getString(), textWidth), textLeft, y + 26, color(0x9099A2, alpha * .85F), false);
    }

    private static int color(int rgb, float alpha) { return ((int)(255 * clamp(alpha)) << 24) | (rgb & 0xFFFFFF); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static float smooth(float value) { return value * value * (3F - 2F * value); }
    private static final class Entry {
        private final ReminderRule rule;
        private long started;
        private int slot;
        private boolean exiting;
        private final boolean overScreen;

        private Entry(ReminderRule rule, long started, int slot, boolean overScreen) {
            this.rule = rule;
            this.started = started;
            this.slot = slot;
            this.overScreen = overScreen;
        }
    }
}
