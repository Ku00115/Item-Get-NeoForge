package com.kuzhi.itemget.client;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.network.ItemGetNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InventoryHandbookButton extends AbstractWidget {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "textures/gui/handbook_button.png");
    private static final int SIZE = 18;
    private static final long DRAG_DELAY_MS = 280;
    private static InventoryHandbookButton current;
    private final int screenWidth;
    private final int screenHeight;
    private boolean pressed;
    private boolean dragging;
    private long pressedAt;
    private double grabX;
    private double grabY;

    public InventoryHandbookButton(int screenWidth, int screenHeight) {
        super(defaultX(screenWidth), defaultY(screenHeight), SIZE, SIZE, Component.translatable("item_get.handbook.title"));
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        if (ClientHooks.inventoryButtonX() >= 0 && ClientHooks.inventoryButtonY() >= 0) {
            setPosition(ClientHooks.inventoryButtonX(), ClientHooks.inventoryButtonY());
        }
        clampToScreen();
        current = this;
    }

    @Override public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        boolean active = isHoveredOrFocused() || dragging;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (active) g.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, 0x55282018);
        g.blit(TEXTURE, getX(), getY(), 0, 0, width, height, width, height);
        RenderSystem.disableBlend();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return begin(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return drag(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return end(x, y, button);
    }

    public static boolean press(Screen screen, double x, double y, int button) {
        return current != null && screen instanceof InventoryScreen && current.begin(x, y, button);
    }

    public static boolean dragCurrent(Screen screen, double x, double y, int button) {
        return current != null && screen instanceof InventoryScreen && current.drag(x, y, button);
    }

    public static boolean release(Screen screen, double x, double y, int button) {
        return current != null && screen instanceof InventoryScreen && current.end(x, y, button);
    }

    private boolean begin(double x, double y, int button) {
        if (button != 0 || !contains(x, y)) return false;
        pressed = true;
        dragging = false;
        pressedAt = Util.getMillis();
        grabX = x - getX();
        grabY = y - getY();
        return true;
    }

    private boolean drag(double x, double y, int button) {
        if (!pressed || button != 0) return false;
        if (!dragging && Util.getMillis() - pressedAt >= DRAG_DELAY_MS) dragging = true;
        if (dragging) {
            setPosition((int)Math.round(x - grabX), (int)Math.round(y - grabY));
            clampToScreen();
        }
        return true;
    }

    private boolean end(double x, double y, int button) {
        if (!pressed || button != 0) return false;
        pressed = false;
        if (dragging) {
            dragging = false;
            ClientHooks.setInventoryButtonPosition(getX(), getY());
            ItemGetNetwork.saveUiLayout(getX(), getY());
        } else if (clicked(x, y)) {
            ItemGetNetwork.requestHistory();
        }
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private void clampToScreen() {
        setPosition(Math.max(2, Math.min(screenWidth - width - 2, getX())), Math.max(2, Math.min(screenHeight - height - 2, getY())));
    }

    private boolean contains(double x, double y) { return x >= getX() && y >= getY() && x < getX() + width && y < getY() + height; }
    private static int defaultX(int screenWidth) { return Math.max(2, (screenWidth + 176) / 2 - SIZE - 4); }
    private static int defaultY(int screenHeight) { return Math.max(2, (screenHeight - 166) / 2 + 4); }
}
