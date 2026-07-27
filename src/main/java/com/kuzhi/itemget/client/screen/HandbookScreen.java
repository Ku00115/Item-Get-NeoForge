package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.ClientHooks;
import com.kuzhi.itemget.client.ConfigIconLibrary;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.TriggerType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HandbookScreen extends CrispScreen {
    private static final int ROW_H = 35;
    private static final int TAB_H = 22;
    private final List<ReminderRule> history;
    private final List<ReminderRule> visibleHistory = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private Filter filter = Filter.ALL;
    private SortField sortField = SortField.TIME;
    private boolean sortDescending = true;
    private int page;
    private int selectedIndex = -1;
    private int detailScroll;
    private int tabScroll;
    private final long openedAt = Util.getMillis();
    private long pageAnimAt;
    private int pageAnimDir;

    public HandbookScreen(List<ReminderRule> history) {
        super(Component.translatable("item_get.handbook.title"));
        this.history = new ArrayList<>(history);
        rebuildVisible();
    }

    @Override protected void init() {
        search = new SearchBox(font, searchX(), bookTop() + 15, searchWidth(), 14, Component.translatable("item_get.handbook.search"));
        search.setHint(Component.translatable("item_get.handbook.search"));
        search.setBordered(false);
        search.setTextColor(0x3C2A1E);
        search.setTextColorUneditable(0x6A5A3A);
        search.setValue(query);
        search.setResponder(value -> {
            query = value == null ? "" : value;
            rebuildVisible(true);
        });
        addRenderableWidget(search);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= tabLeft() - 3 && x <= tabLeft() + tabWidth() + 8 && y >= tabViewportTop() && y <= tabViewportBottom()) {
            tabScroll = Math.max(0, Math.min(maxTabScroll(), tabScroll - (int)Math.signum(scrollY) * tabPageStep()));
            return true;
        }
        if (x >= rightPageLeft() && x <= rightPageRight() && y >= detailTextTop() && y <= bookBottom() - 30 && maxDetailScroll() > 0) {
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll - (int)Math.signum(scrollY)));
            return true;
        }
        if (x >= bookLeft() && x <= bookRight() && y >= bookTop() && y <= bookBottom()) {
            changePage(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (search != null && search.isMouseOver(x, y)) return super.mouseClicked(x, y, button);
        if (button != 0) return false;
        int tab = tabAt(x, y);
        if (tab >= 0) {
            filter = Filter.values()[tab];
            rebuildVisible(true);
            return true;
        }
        if (sortHit(x, y)) {
            sortField = sortField == SortField.TIME ? SortField.NAME : SortField.TIME;
            rebuildVisible(true);
            return true;
        }
        if (sortDirectionHit(x, y)) {
            sortDescending = !sortDescending;
            rebuildVisible(true);
            return true;
        }
        if (previousHit(x, y)) {
            changePage(-1);
            return true;
        }
        if (nextHit(x, y)) {
            changePage(1);
            return true;
        }
        PonderHit ponderEntry = ponderAt(x, y);
        if (ponderEntry != null) {
            openPonder(visibleHistory.get(ponderEntry.index()), ponderEntry.kind());
            return true;
        }
        LinkKind detailPonder = detailPonderAt(x, y);
        if (detailPonder != LinkKind.NONE && selectedRule() != null) {
            openPonder(selectedRule(), detailPonder);
            return true;
        }
        if (detailHit(x, y) && selectedRule() != null && isUnlocked(selectedRule())) {
            minecraft.setScreen(new ReminderScreen(selectedRule(), this));
            return true;
        }
        int index = rowAt(x, y);
        if (index >= 0) {
            if (selectedIndex != index) detailScroll = 0;
            selectedIndex = index;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x66000000);
        drawCentered(g, title, width / 2, 10, 0xF4E8C8);
        g.pose().pushPose();
        applyOpenPose(g);
        renderBook(g);
        renderTabs(g, mx, my);
        renderSearchFrame(g, mx, my);
        renderCrispWidgets(g, mx, my, partial);
        if (history.isEmpty()) {
            g.drawString(font, Component.translatable("item_get.handbook.empty"), leftPageLeft() + 10, bookTop() + 54, 0x6A5A3A, false);
            renderPageTurn(g);
            g.pose().popPose();
            return;
        }
        if (visibleHistory.isEmpty()) {
            g.drawString(font, Component.translatable("item_get.handbook.no_results"), leftPageLeft() + 10, bookTop() + 54, 0x6A5A3A, false);
            renderPageTurn(g);
            g.pose().popPose();
            return;
        }
        ensureSelection();
        renderList(g, mx, my);
        renderDetail(g, mx, my);
        renderPageButtons(g, mx, my);
        renderPageTurn(g);
        g.pose().popPose();
    }

    private void applyOpenPose(GuiGraphics g) {
        float p = ease(progress(openedAt, 180));
        float scale = .94F + .06F * p;
        int cx = width / 2, cy = bookTop() + bookHeight() / 2;
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, -cy, 0);
    }

    private void renderBook(GuiGraphics g) {
        int left = bookLeft(), top = bookTop(), right = bookRight(), bottom = bookBottom(), mid = width / 2;
        g.fill(left + 4, top + 5, right + 4, bottom + 5, 0x66000000);
        g.fill(left, top, right, bottom, 0xFF4A3526);
        g.fill(left + 5, top + 5, mid - 2, bottom - 5, 0xFFE0D0A4);
        g.fill(mid + 2, top + 5, right - 5, bottom - 5, 0xFFE7D9B4);
        g.fillGradient(mid - 3, top + 6, mid + 4, bottom - 6, 0, 0x70513A28, 0x30513A28);
        g.fill(left + 8, top + 8, mid - 6, top + 9, 0x55FFFFFF);
        g.fill(mid + 7, top + 8, right - 8, top + 9, 0x55FFFFFF);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        tabScroll = Math.max(0, Math.min(maxTabScroll(), tabScroll));
        Filter[] values = Filter.values();
        g.enableScissor(tabLeft() - 3, tabViewportTop() - 2, tabLeft() + tabWidth() + 8, tabViewportBottom() + 2);
        for (int i = 0; i < values.length; i++) {
            int x = tabLeft(), y = tabTop(i), w = tabWidth();
            if (y + TAB_H < tabViewportTop() || y > tabViewportBottom()) continue;
            boolean active = values[i] == filter, hover = mx >= x && mx <= x + w && my >= y && my <= y + TAB_H;
            int fill = values[i].color;
            if (hover) fill = brighten(fill);
            if (active) x += 4;
            g.fill(x - 1, y - 1, x + w + 1, y + TAB_H + 1, 0xFF3E2B1E);
            g.fill(x, y, x + w, y + TAB_H, fill);
            g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x44FFFFFF);
            String text = Component.translatable(values[i].key).getString();
            g.drawString(font, font.plainSubstrByWidth(text, w - 6), x + 3, y + 7, 0xF8EDD0, false);
        }
        g.disableScissor();
    }

    private void renderSearchFrame(GuiGraphics g, int mx, int my) {
        int x = searchX(), y = bookTop() + 14, w = searchWidth(), h = 14;
        g.fill(x, y + h - 1, x + w, y + h, 0x667D6846);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x33FFFFFF);
        String text = search == null ? query : search.getValue();
        boolean empty = text == null || text.isEmpty();
        String shown = empty ? Component.translatable("item_get.handbook.search").getString() : text;
        int color = empty ? 0x8A7655 : 0x3C2A1E;
        String clipped = font.plainSubstrByWidth(shown, Math.max(10, w - 8));
        g.drawString(font, clipped, x + 3, y + 3, color, false);
        if (search != null && search.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cx = x + 3 + font.width(clipped);
            g.fill(cx, y + 3, cx + 1, y + 12, 0xFF3C2A1E);
        }
        drawTextButton(g, sortX(), y - 1, sortButtonSize(), 14, sortField.label, sortHit(mx, my));
        drawTextButton(g, sortDirectionX(), y - 1, sortButtonSize(), 14, sortDescending ? "\u2193" : "\u2191", sortDirectionHit(mx, my));
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int start = page * pageSize(), end = Math.min(visibleHistory.size(), start + pageSize());
        int left = leftPageLeft(), right = leftPageRight(), y = listTop();
        for (int index = start; index < end; index++, y += ROW_H) {
            ReminderRule rule = visibleHistory.get(index);
            boolean unlocked = isUnlocked(rule);
            boolean selected = index == selectedIndex, hover = mx >= left && mx <= right && my >= y && my < y + ROW_H - 3;
            if (selected || hover) g.fill(left - 3, y - 2, right + 2, y + ROW_H - 4, selected ? 0x66513A28 : 0x33FFFFFF);
            drawIconFrame(g, left + 2, y + 4, 20);
            boolean image = unlocked && ConfigIconLibrary.render(g, rule, left + 11, y + 14, .82F);
            if (!image && unlocked) {
                g.pose().pushPose();
                g.pose().translate(left + 11, y + 14, 0);
                g.pose().scale(.82F, .82F, 1);
                g.renderItem(ManagerScreen.displayStack(rule), -8, -8);
                g.pose().popPose();
            }
            if (!unlocked) drawLockMark(g, left + 7, y + 8, 0x6A5A3A);
            List<LinkKind> linkKinds = availableLinkKinds(rule);
            int textLeft = left + 25, textRight = right - (linkKinds.isEmpty() ? 2 : linkKinds.size() * 19 + 3);
            g.drawString(font, font.plainSubstrByWidth(listTitleOf(rule), Math.max(30, textRight - textLeft)), textLeft, y + 2, unlocked ? 0x3C2A1E : 0x76614A, false);
            g.drawString(font, font.plainSubstrByWidth(subtitleOf(rule).getString(), Math.max(30, textRight - textLeft)), textLeft, y + 13, unlocked ? 0x6A5A3A : 0x8A7655, false);
            drawLinkButtons(g, linkKinds, right, y + 5, mx, my);
        }
    }

    private void renderDetail(GuiGraphics g, int mx, int my) {
        ReminderRule rule = selectedRule();
        if (rule == null) return;
        boolean unlocked = isUnlocked(rule);
        int left = rightPageLeft(), top = detailTop(), right = rightPageRight();
        int iconSize = 42, iconX = left + 14, iconY = top + 6;
        drawIconFrame(g, iconX, iconY, iconSize);
        boolean image = unlocked && ConfigIconLibrary.render(g, rule, iconX + iconSize / 2, iconY + iconSize / 2, 1.35F);
        if (!image && unlocked) {
            g.pose().pushPose();
            g.pose().translate(iconX + iconSize / 2, iconY + iconSize / 2, 0);
            g.pose().scale(1.35F, 1.35F, 1);
            g.renderItem(ManagerScreen.displayStack(rule), -8, -8);
            g.pose().popPose();
        }
        if (!unlocked) drawLockMark(g, iconX + 15, iconY + 13, 0x6A5A3A);
        int textLeft = iconX + iconSize + 10, textW = Math.max(40, right - textLeft - 4);
        g.drawString(font, font.plainSubstrByWidth(titleOf(rule), textW), textLeft, top + 12, unlocked ? 0x3C2A1E : 0x76614A, false);
        g.drawString(font, font.plainSubstrByWidth(subtitleOf(rule).getString(), textW), textLeft, top + 25, unlocked ? 0x6A5A3A : 0x8A7655, false);
        String meta = metadataOf(rule);
        if (!meta.isBlank()) g.drawString(font, font.plainSubstrByWidth(meta, textW), textLeft, top + 38, 0x8A7655, false);
        int textTop = detailTextTop();
        List<FormattedCharSequence> lines = detailLines();
        int maxLines = Math.max(1, (bookBottom() - textTop - 34) / 11);
        detailScroll = Math.max(0, Math.min(maxDetailScroll(lines, maxLines), detailScroll));
        int count = Math.min(maxLines, Math.max(0, lines.size() - detailScroll));
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(detailScroll + i), left + 4, textTop + i * 11, 0x4E4130, false);
        if (maxDetailScroll(lines, maxLines) > 0) {
            int track = maxLines * 11 - 2, thumb = Math.max(9, track * maxLines / lines.size()), thumbY = textTop + (track - thumb) * detailScroll / maxDetailScroll(lines, maxLines);
            g.fill(right - 3, textTop, right - 2, textTop + track, 0x447D6846);
            g.fill(right - 4, thumbY, right - 1, thumbY + thumb, 0xAA7D6846);
        }
        if (unlocked) drawTextButton(g, left + 4, bookBottom() - 23, 42, 14, Component.translatable("item_get.handbook.detail").getString(), detailHit(mx, my));
        drawDetailLinkButtons(g, availableLinkKinds(rule), mx, my);
    }

    private void renderPageButtons(GuiGraphics g, int mx, int my) {
        int y = bookBottom() - 22;
        drawTextButton(g, leftPageLeft(), y, 18, 14, "<", previousHit(mx, my));
        drawTextButton(g, leftPageRight() - 18, y, 18, 14, ">", nextHit(mx, my));
        drawCentered(g, Component.translatable("item_get.manager.page", page + 1, pageCount()), (leftPageLeft() + leftPageRight()) / 2, y + 3, 0x6A5A3A);
    }

    private void renderPageTurn(GuiGraphics g) {
        if (pageAnimAt <= 0) return;
        float p = progress(pageAnimAt, 230);
        if (p >= 1F) return;
        float e = ease(p);
        int top = bookTop() + 6, bottom = bookBottom() - 6, mid = width / 2;
        int left = leftPageLeft() - 6, right = rightPageRight() + 6;
        int x = pageAnimDir >= 0 ? right - (int)((right - left) * e) : left + (int)((right - left) * e);
        int curve = ((int)(92 * (1F - Math.abs(.5F - p) * 2F)) << 24) | 0x513A28;
        int paper = ((int)(42 * (1F - Math.abs(.5F - p) * 1.25F)) << 24) | 0xFFFFFF;
        int shadow = ((int)(36 * (1F - Math.abs(.5F - p) * 1.5F)) << 24) | 0x000000;
        g.fill(left, top, right, bottom, shadow);
        if (pageAnimDir >= 0) {
            g.fill(Math.max(left, x), top, right, bottom, paper);
            g.fill(Math.max(left, x - 3), top, Math.min(right, x + 3), bottom, curve);
            if (x < mid) g.fill(x, top, mid, bottom, 0x14513A28);
        } else {
            g.fill(left, top, Math.min(right, x), bottom, paper);
            g.fill(Math.max(left, x - 3), top, Math.min(right, x + 3), bottom, curve);
            if (x > mid) g.fill(mid, top, x, bottom, 0x14513A28);
        }
        g.fill(mid - 2, top, mid + 3, bottom, 0x33513A28);
    }

    private void drawLinkButtons(GuiGraphics g, List<LinkKind> kinds, int right, int y, int mx, int my) {
        int start = right - kinds.size() * 19;
        for (int i = 0; i < kinds.size(); i++) {
            int x = start + i * 19;
            drawTextButton(g, x + 2, y + 1, 15, 14, kinds.get(i).shortLabel, mx >= x && mx <= x + 18 && my >= y && my <= y + 16);
        }
    }

    private void drawDetailLinkButtons(GuiGraphics g, List<LinkKind> kinds, int mx, int my) {
        if (kinds.isEmpty()) return;
        int y = bookBottom() - 23, x = detailLinkStart(kinds);
        for (LinkKind kind : kinds) {
            int w = kind.detailWidth;
            drawTextButton(g, x, y, w, 14, kind.detailLabel, detailLinkHit(mx, my, kind, x, y, w));
            x += w + 4;
        }
    }

    private void drawTextButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xCC6D5134 : 0xAA513A28);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0x55EAD9B4 : 0x33EAD9B4);
        drawCentered(g, font.plainSubstrByWidth(text, w - 4), x + w / 2, y + (h - 8) / 2, 0xF8EDD0);
    }

    private void drawCentered(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    private void drawCentered(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    private void drawIconFrame(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xAA7D6846);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x66F8EDD0);
        g.fill(x + 2, y + 2, x + size - 2, y + 3, 0x44FFFFFF);
    }

    private void drawLockMark(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 7, x + 12, y + 16, color);
        g.fill(x + 4, y + 5, x + 6, y + 8, color);
        g.fill(x + 9, y + 5, x + 11, y + 8, color);
        g.fill(x + 5, y + 3, x + 10, y + 5, color);
    }

    private void rebuildVisible() {
        rebuildVisible(false);
    }

    private void rebuildVisible(boolean resetPosition) {
        visibleHistory.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (ReminderRule rule : history) if (filter.matches(rule) && (q.isEmpty() || matches(rule, q))) visibleHistory.add(rule);
        java.util.Comparator<ReminderRule> comparator = sortField == SortField.NAME
                ? java.util.Comparator.<ReminderRule, String>comparing(this::listTitleOf, String.CASE_INSENSITIVE_ORDER).thenComparingInt(this::orderOf)
                : java.util.Comparator.comparingInt(this::timeOrderOf);
        visibleHistory.sort(sortDescending ? comparator.reversed() : comparator);
        if (resetPosition) {
            page = 0;
            selectedIndex = -1;
            detailScroll = 0;
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
        ensureSelection();
    }

    private boolean matches(ReminderRule rule, String q) {
        String text = (titleOf(rule) + "\n" + descriptionOf(rule) + "\n" + subtitleOf(rule).getString() + "\n" + nullToEmpty(rule.entryNumber) + "\n" + nullToEmpty(rule.category) + "\n" + nullToEmpty(rule.group) + "\n" + rule.id).toLowerCase(Locale.ROOT);
        return text.contains(q);
    }
    private int orderOf(ReminderRule rule) {
        int order = handbookOrder(rule);
        if (order >= 0) return order;
        return rule.sort != 0 ? rule.sort : history.indexOf(rule);
    }
    private int timeOrderOf(ReminderRule rule) { int order = handbookOrder(rule); return order >= 0 ? order : (sortDescending ? Integer.MIN_VALUE : Integer.MAX_VALUE); }
    private int handbookOrder(ReminderRule rule) { return rule.trigger != null && rule.trigger.has("_handbook_order") ? rule.trigger.get("_handbook_order").getAsInt() : -1; }

    private void ensureSelection() {
        if (visibleHistory.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= visibleHistory.size()) selectedIndex = page * pageSize();
        if (font != null) detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll));
    }

    private ReminderRule selectedRule() {
        return selectedIndex >= 0 && selectedIndex < visibleHistory.size() ? visibleHistory.get(selectedIndex) : null;
    }

    private int rowAt(double x, double y) {
        if (x < leftPageLeft() || x > leftPageRight() || y < listTop() || y > listBottom()) return -1;
        int row = ((int) y - listTop()) / ROW_H;
        int index = page * pageSize() + row;
        return row >= 0 && row < pageSize() && index >= 0 && index < visibleHistory.size() ? index : -1;
    }

    private PonderHit ponderAt(double x, double y) {
        int index = rowAt(x, y);
        if (index < 0) return null;
        List<LinkKind> kinds = availableLinkKinds(visibleHistory.get(index));
        if (kinds.isEmpty()) return null;
        int rowY = listTop() + (index - page * pageSize()) * ROW_H;
        int start = leftPageRight() - kinds.size() * 19, by = rowY + 5;
        for (int i = 0; i < kinds.size(); i++) {
            int bx = start + i * 19;
            if (x >= bx && x <= bx + 18 && y >= by && y <= by + 16) return new PonderHit(index, kinds.get(i));
        }
        return null;
    }

    private int tabAt(double x, double y) {
        for (int i = 0; i < Filter.values().length; i++) if (x >= tabLeft() && x <= tabLeft() + tabWidth() && y >= tabTop(i) && y <= tabTop(i) + TAB_H) return i;
        return -1;
    }

    private void changePage(int amount) {
        int oldPage = page;
        page = Math.max(0, Math.min(pageCount() - 1, page + amount));
        if (page != oldPage) {
            pageAnimAt = Util.getMillis();
            pageAnimDir = Integer.signum(amount);
        }
        if (selectedIndex < page * pageSize() || selectedIndex >= page * pageSize() + pageSize()) selectedIndex = Math.min(visibleHistory.size() - 1, page * pageSize());
    }

    private boolean previousHit(double x, double y) { return x >= leftPageLeft() && x <= leftPageLeft() + 18 && y >= bookBottom() - 22 && y <= bookBottom() - 8; }
    private boolean nextHit(double x, double y) { return x >= leftPageRight() - 18 && x <= leftPageRight() && y >= bookBottom() - 22 && y <= bookBottom() - 8; }
    private boolean detailHit(double x, double y) { return x >= rightPageLeft() + 4 && x <= rightPageLeft() + 46 && y >= bookBottom() - 23 && y <= bookBottom() - 9; }
    private boolean sortHit(double x, double y) { return x >= sortX() && x <= sortX() + sortButtonSize() && y >= bookTop() + 13 && y <= bookTop() + 27; }
    private boolean sortDirectionHit(double x, double y) { return x >= sortDirectionX() && x <= sortDirectionX() + sortButtonSize() && y >= bookTop() + 13 && y <= bookTop() + 27; }
    private LinkKind detailPonderAt(double x, double y) {
        ReminderRule rule = selectedRule();
        if (rule == null) return LinkKind.NONE;
        List<LinkKind> kinds = availableLinkKinds(rule);
        int py = bookBottom() - 23, px = detailLinkStart(kinds);
        for (LinkKind kind : kinds) {
            int w = kind.detailWidth;
            if (detailLinkHit(x, y, kind, px, py, w)) return kind;
            px += w + 4;
        }
        return LinkKind.NONE;
    }
    private boolean detailLinkHit(double x, double y, LinkKind kind, int px, int py, int w) { return kind != LinkKind.NONE && x >= px && x <= px + w && y >= py && y <= py + 14; }
    private int detailLinkStart(List<LinkKind> kinds) {
        int total = -4;
        for (LinkKind kind : kinds) total += kind.detailWidth + 4;
        return rightPageRight() - Math.max(0, total) - 6;
    }
    private static boolean hasPonder(ReminderRule rule) { return isUnlocked(rule) && rule.ponderTarget != null && !rule.ponderTarget.isBlank(); }
    private List<LinkKind> availableLinkKinds(ReminderRule rule) {
        boolean unlocked = isUnlocked(rule);
        if (!unlocked) {
            ClientHooks.logJeiButtonState(rule, false, false, ItemStack.EMPTY);
            return List.of();
        }
        List<LinkKind> out = new ArrayList<>();
        boolean hasJei = ClientHooks.hasJei();
        ItemStack jeiStack = ManagerScreen.jeiStack(rule);
        ClientHooks.logJeiButtonState(rule, true, hasJei, jeiStack);
        if (hasJei && !jeiStack.isEmpty()) out.add(LinkKind.JEI);
        if (hasPonder(rule)) {
            if (ClientHooks.hasCreatePonderScene(rule.ponderTarget)) out.add(LinkKind.CREATE);
            if (ClientHooks.hasPondererScene(rule.ponderTarget)) out.add(LinkKind.PONDERER);
        }
        return out;
    }
    private void openPonder(ReminderRule rule, LinkKind kind) {
        if (kind == LinkKind.JEI) ClientHooks.openJei(ManagerScreen.jeiStack(rule), ManagerScreen.jeiMode(rule));
        else if (kind == LinkKind.CREATE) ClientHooks.openCreatePonder(rule.ponderTarget);
        else if (kind == LinkKind.PONDERER) ClientHooks.openPonderer(rule.ponderTarget);
    }
    private static boolean isUnlocked(ReminderRule rule) {
        return rule == null || rule.trigger == null || !rule.trigger.has("_handbook_unlocked") || rule.trigger.get("_handbook_unlocked").getAsBoolean();
    }
    private String titleOf(ReminderRule rule) {
        if (!isUnlocked(rule)) {
            String value = TranslatedText.resolve(rule.lockedTitle);
            if (value.isBlank()) value = Component.translatable("item_get.handbook.locked.title").getString();
            return value;
        }
        String value = rule.title == null || rule.title.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : TranslatedText.resolve(rule.title);
        return value.isBlank() ? Component.translatable("item_get.manager.unnamed").getString() : value;
    }
    private String listTitleOf(ReminderRule rule) {
        String title = titleOf(rule);
        String number = nullToEmpty(rule.entryNumber).trim();
        return number.isBlank() ? title : number + "  " + title;
    }
    private Component subtitleOf(ReminderRule rule) {
        if (!isUnlocked(rule)) {
            String value = TranslatedText.resolve(rule.lockedSubtitle);
            return value.isBlank() ? Component.translatable("item_get.handbook.locked.subtitle") : Component.literal(value);
        }
        return ManagerScreen.displaySubtitle(rule, Util.getMillis() - openedAt);
    }
    private String descriptionOf(ReminderRule rule) {
        if (!isUnlocked(rule)) {
            String value = TranslatedText.resolve(rule.lockedDescription);
            return value.isBlank() ? Component.translatable("item_get.handbook.locked.description").getString() : value;
        }
        return TranslatedText.resolve(rule.description);
    }
    private String metadataOf(ReminderRule rule) {
        List<String> parts = new ArrayList<>();
        if (!nullToEmpty(rule.entryNumber).isBlank()) parts.add(rule.entryNumber.trim());
        if (!nullToEmpty(rule.category).isBlank()) parts.add(TranslatedText.resolve(rule.category).trim());
        if (!nullToEmpty(rule.group).isBlank()) parts.add(TranslatedText.resolve(rule.group).trim());
        return String.join(" / ", parts);
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private List<FormattedCharSequence> detailLines() { ReminderRule rule = selectedRule(); return rule == null ? List.of() : font.split(Component.literal(descriptionOf(rule)), pageWidth() - 14); }
    private int detailTextTop() { return detailTop() + 78; }
    private int detailMaxLines() { return Math.max(1, (bookBottom() - detailTextTop() - 30) / 11); }
    private int maxDetailScroll() { return maxDetailScroll(detailLines(), detailMaxLines()); }
    private int maxDetailScroll(List<FormattedCharSequence> lines, int maxLines) { return Math.max(0, lines.size() - maxLines); }

    private int bookWidth() { return Math.min(width - 70, 520); }
    private int bookHeight() { return Math.min(height - 42, 310); }
    private int bookLeft() { return width / 2 - bookWidth() / 2 + 12; }
    private int bookRight() { return bookLeft() + bookWidth(); }
    private int bookTop() { return 27; }
    private int bookBottom() { return bookTop() + bookHeight(); }
    private int pageWidth() { return bookWidth() / 2 - 32; }
    private int leftPageLeft() { return bookLeft() + 17; }
    private int leftPageRight() { return width / 2 - 12; }
    private int rightPageLeft() { return width / 2 + 17; }
    private int rightPageRight() { return bookRight() - 17; }
    private int listTop() { return bookTop() + 38; }
    private int listBottom() { return bookBottom() - 31; }
    private int detailTop() { return bookTop() + 38; }
    private int pageSize() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }
    private int pageCount() { return Math.max(1, (visibleHistory.size() + pageSize() - 1) / pageSize()); }
    private int tabWidth() { return 34; }
    private int tabLeft() { return Math.max(4, bookLeft() - tabWidth() + 4); }
    private int tabTop(int index) { return tabViewportTop() + index * (TAB_H + 3) - tabScroll; }
    private int tabViewportTop() { return bookTop() + 18; }
    private int tabViewportBottom() { return bookBottom() - 10; }
    private int maxTabScroll() { return Math.max(0, Filter.values().length * (TAB_H + 3) - 3 - (tabViewportBottom() - tabViewportTop())); }
    private int tabPageStep() { return Math.max(TAB_H + 3, ((tabViewportBottom() - tabViewportTop()) / (TAB_H + 3)) * (TAB_H + 3)); }
    private int searchX() { return leftPageLeft(); }
    private int searchWidth() { return Math.max(55, pageWidth() - sortButtonsWidth() - 10); }
    private int sortButtonSize() { return 16; }
    private int sortButtonsWidth() { return sortButtonSize() * 2 + 4; }
    private int sortWidth() { return sortButtonsWidth(); }
    private int sortX() { return searchX() + searchWidth() + 6; }
    private int sortDirectionX() { return sortX() + sortButtonSize() + 4; }
    private static float progress(long started, int duration) { return Math.min(1F, Math.max(0F, (Util.getMillis() - started) / (float) duration)); }
    private static float ease(float t) { float inv = 1F - t; return 1F - inv * inv * inv; }
    private static int brighten(int color) {
        int a = color & 0xFF000000;
        int r = Math.min(255, ((color >> 16) & 255) + 18);
        int g = Math.min(255, ((color >> 8) & 255) + 18);
        int b = Math.min(255, (color & 255) + 18);
        return a | (r << 16) | (g << 8) | b;
    }

    private static final class SearchBox extends EditBox {
        private SearchBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        }
    }

    private enum Filter {
        ALL("item_get.handbook.filter.all", 0xFFB88A57),
        ITEMS("item_get.handbook.filter.items", 0xFF9E7447),
        ENTITY("item_get.handbook.filter.entity", 0xFF8F6B53),
        WORLD("item_get.handbook.filter.world", 0xFF6E8A67),
        PLAYER("item_get.handbook.filter.player", 0xFF8D735D),
        ADVANCEMENT("item_get.handbook.filter.advancement", 0xFF7B6D91),
        PONDER("item_get.handbook.filter.ponder", 0xFF5F7E91);

        private final String key;
        private final int color;
        Filter(String key, int color) { this.key = key; this.color = color; }
        private boolean matches(ReminderRule rule) {
            TriggerType type = TriggerType.parse(rule.triggerType);
            return switch (this) {
                case ALL -> true;
                case ITEMS -> type == TriggerType.ITEM_ACQUIRED;
                case ENTITY -> type == TriggerType.ENTITY_KILLED || type == TriggerType.OBSERVE_ENTITY;
                case WORLD -> type == TriggerType.WEATHER_IS || type == TriggerType.TIME_IS || type == TriggerType.ENTER_BIOME || type == TriggerType.ENTER_STRUCTURE || type == TriggerType.DIMENSION_CHANGED || type == TriggerType.MANUAL;
                case PLAYER -> type == TriggerType.HEALTH_AT || type == TriggerType.HUNGER_AT || type == TriggerType.EFFECT_GAINED || type == TriggerType.DEATH_BY || type == TriggerType.OBSERVE_BLOCK || type == TriggerType.HOVER_ITEM;
                case ADVANCEMENT -> type == TriggerType.ADVANCEMENT_DONE;
                case PONDER -> isUnlocked(rule) && ClientHooks.hasPonderScene(rule.ponderTarget);
            };
        }
    }

    private enum SortField {
        TIME("T"),
        NAME("A");
        private final String label;
        SortField(String label) { this.label = label; }
    }

    private enum LinkKind {
        NONE("", "", 0),
        JEI("J", "JEI", 28),
        CREATE("C", "Create", 46),
        PONDERER("P", "Ponderer", 58);

        private final String shortLabel;
        private final String detailLabel;
        private final int detailWidth;
        LinkKind(String shortLabel, String detailLabel, int detailWidth) {
            this.shortLabel = shortLabel;
            this.detailLabel = detailLabel;
            this.detailWidth = detailWidth;
        }
    }

    private record PonderHit(int index, LinkKind kind) {}
}
