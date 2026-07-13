package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class HandbookScreen extends CrispScreen {
    private static final int ROW_H = 42;
    private static final int SCROLL_STEP = 18;
    private final List<ReminderRule> history;
    private int scrollPixels;

    public HandbookScreen(List<ReminderRule> history) {
        super(Component.translatable("item_get.handbook.title"));
        this.history = new ArrayList<>(history);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollPixels = clampScroll(scrollPixels - (int)Math.signum(scrollY) * SCROLL_STEP);
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        int index = rowAt(x, y);
        if (button == 0 && index >= 0) {
            minecraft.setScreen(new ReminderScreen(history.get(index), this));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (history.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("item_get.handbook.empty"), width / 2, height / 2 - 5, 0xA0A0A0);
            return;
        }
        int left = Math.max(16, width / 2 - 210), right = Math.min(width - 16, width / 2 + 210);
        int top = listTop(), bottom = listBottom();
        scrollPixels = clampScroll(scrollPixels);
        g.enableScissor(left, top, right, bottom);
        int first = Math.max(0, scrollPixels / ROW_H);
        int offset = scrollPixels % ROW_H;
        for (int index = first; index < history.size(); index++) {
            int y = top + (index - first) * ROW_H - offset;
            if (y >= bottom) break;
            ReminderRule rule = history.get(index);
            boolean hover = mx >= left && mx <= right && my >= y && my < y + ROW_H - 4;
            g.fill(left, y, right, y + ROW_H - 4, hover ? 0xAA405A72 : 0x88303030);
            g.renderItem(ManagerScreen.displayStack(rule), left + 8, y + 11);
            String titleText = rule.title == null || rule.title.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : TranslatedText.resolve(rule.title);
            g.drawString(font, titleText, left + 32, y + 6, 0xFFFFFF);
            g.drawString(font, ManagerScreen.triggerSummary(rule), left + 32, y + 18, 0xC8D0D8);
            String desc = TranslatedText.resolve(rule.description);
            if (!desc.isBlank()) g.drawString(font, font.plainSubstrByWidth(desc, Math.max(20, right - left - 40)), left + 32, y + 29, 0x9099A2);
        }
        g.disableScissor();
        if (contentHeight() > viewportHeight()) {
            int trackTop = top, trackBottom = bottom, track = trackBottom - trackTop;
            int thumb = Math.max(12, track * viewportHeight() / contentHeight());
            int thumbY = trackTop + (track - thumb) * scrollPixels / Math.max(1, maxScroll());
            g.fill(right + 4, trackTop, right + 5, trackBottom, 0x44FFFFFF);
            g.fill(right + 3, thumbY, right + 6, thumbY + thumb, 0xAAFFFFFF);
        }
    }

    private int listTop() { return 34; }
    private int listBottom() { return Math.max(listTop() + ROW_H, height - 14); }
    private int viewportHeight() { return Math.max(1, listBottom() - listTop()); }
    private int contentHeight() { return history.size() * ROW_H; }
    private int maxScroll() { return Math.max(0, contentHeight() - viewportHeight()); }
    private int clampScroll(int value) { return Math.max(0, Math.min(maxScroll(), value)); }
    private int rowAt(double x, double y) {
        int left = Math.max(16, width / 2 - 210), right = Math.min(width - 16, width / 2 + 210);
        int top = listTop(), bottom = listBottom();
        int index = ((int)y - top + scrollPixels) / ROW_H;
        return x >= left && x <= right && y >= top && y < bottom && index >= 0 && index < history.size() ? index : -1;
    }
}
