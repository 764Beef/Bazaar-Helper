package com.bazaarhelper.gui;

import com.bazaarhelper.util.BazaarOrder;
import com.bazaarhelper.util.BazaarOrderParser;
import com.bazaarhelper.util.PickupSellAutomation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class BazaarOrderPanel {

    private static final int PANEL_W  = 200;
    private static final int HEADER_H = 26;
    private static final int ROW_H    = 46;
    private static final int PAD      = 6;
    private static final int BTN_W    = 80;
    private static final int BTN_H    = 13;

    private static final int C_BG        = 0xEE0D0D0D;
    private static final int C_HEADER    = 0xFF0F2D0F;
    private static final int C_ROW_A     = 0xFF141414;
    private static final int C_ROW_B     = 0xFF181818;
    private static final int C_BORDER    = 0xFF1F6B1F;
    private static final int C_BTN       = 0xFF145214;
    private static final int C_BTN_HOVER = 0xFF1F8C1F;
    private static final int C_BTN_DIS   = 0xFF2A2A2A;
    private static final int C_MAX       = 0xFF2D6600;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_GOLD      = 0xFFFFAA00;
    private static final int C_GREEN     = 0xFF55FF55;
    private static final int C_GRAY      = 0xFFAAAAAA;
    private static final int C_YELLOW    = 0xFFFFFF55;

    private final List<BazaarOrder> orders;
    private final int px, py;

    public BazaarOrderPanel(List<BazaarOrder> orders,
                            int guiX, int guiY, int guiW, int guiH,
                            int screenW, int screenH) {
        this.orders = orders != null ? orders : new ArrayList<>();
        int panelH = totalHeight();
        int rightX = guiX + guiW + 4;

        if (rightX + PANEL_W <= screenW) {
            this.px = rightX;
            this.py = Math.min(guiY, screenH - panelH - 2);
        } else {
            this.px = Math.max(2, guiX + (guiW - PANEL_W) / 2);
            this.py = guiY + guiH + 4;
        }
    }

    private static void drawText(DrawContext ctx, TextRenderer font, String text, int x, int y, int color) {
        ctx.drawText(font, Text.literal(text), x, y, color, false);
    }

    public void render(DrawContext ctx, TextRenderer font, int mx, int my) {
        int panelH = totalHeight();

        ctx.fill(px - 1, py - 1, px + PANEL_W + 1, py + panelH + 1, C_BORDER);
        ctx.fill(px, py, px + PANEL_W, py + panelH, C_BG);

        ctx.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
        drawText(ctx, font, "§a§lBazaar Helper", px + PAD, py + 4, C_WHITE);
        drawText(ctx, font, "§7Buy Orders with claimable items", px + PAD, py + 14, C_GRAY);

        if (orders.isEmpty()) {
            drawText(ctx, font, "§7No filled orders.", px + PAD, py + HEADER_H + PAD, C_GRAY);
            return;
        }

        boolean automating = PickupSellAutomation.getInstance() != null &&
                PickupSellAutomation.getInstance().isRunning();

        for (int i = 0; i < orders.size(); i++) {
            int rowY = py + HEADER_H + i * ROW_H;

            ctx.fill(px, rowY, px + PANEL_W, rowY + ROW_H,
                    (i % 2 == 0) ? C_ROW_A : C_ROW_B);

            renderRow(ctx, font, orders.get(i), rowY, mx, my, automating);
        }

        if (automating) {
            int footerY = py + panelH - 12;
            ctx.fill(px, footerY, px + PANEL_W, py + panelH, 0xFF0A200A);

            drawText(ctx, font,
                    "§e⟳ " + PickupSellAutomation.getInstance().getState().name(),
                    px + PAD, footerY + 2, C_YELLOW);
        }
    }

    private void renderRow(DrawContext ctx, TextRenderer font, BazaarOrder o,
                           int rowY, int mx, int my, boolean automating) {

        int lx = px + PAD;
        int ty = rowY + 4;

        String name = o.itemName != null ? o.itemName : "Unknown Item";
        drawText(ctx, font, "§f" + clamp(name, 18), lx, ty, C_WHITE);

        String fillStr = "§a" + BazaarOrderParser.formatNumber(o.filledAmount)
                + "§7/§f" + BazaarOrderParser.formatNumber(o.totalAmount);

        drawText(ctx, font, fillStr, lx, ty + 10, C_GREEN);

        if (o.isFullyFilled()) {
            int bx = lx + 72;
            ctx.fill(bx, ty + 9, bx + 26, ty + 18, C_MAX);
            drawText(ctx, font, "§lMAX", bx + 2, ty + 10, C_WHITE);
        }

        drawText(ctx, font,
                "§6" + BazaarOrderParser.formatCoins(o.totalFilledValue()) + " coins",
                lx, ty + 20, C_GOLD);

        int btnX = px + PANEL_W - BTN_W - PAD;
        int btnY = rowY + (ROW_H - BTN_H) / 2;

        boolean hovered = !automating && isMouse(mx, my, btnX, btnY, BTN_W, BTN_H);
        drawButton(ctx, font, btnX, btnY, "⬆ Pickup & Sell", hovered, automating);
    }

    private void drawButton(DrawContext ctx, TextRenderer font,
                            int x, int y, String label,
                            boolean hovered, boolean disabled) {

        int border = disabled ? 0xFF1A1A1A : (hovered ? 0xFF00AA00 : 0xFF006600);
        int bg     = disabled ? C_BTN_DIS  : (hovered ? C_BTN_HOVER : C_BTN);

        ctx.fill(x - 1, y - 1, x + BTN_W + 1, y + BTN_H + 1, border);
        ctx.fill(x, y, x + BTN_W, y + BTN_H, bg);

        int textCol = disabled ? C_GRAY : C_WHITE;
        int tx = x + (BTN_W - font.getWidth(label)) / 2;

        drawText(ctx, font, label, tx, y + 3, textCol);
    }

    public BazaarOrder onMouseClick(double mx, double my) {
        if (orders.isEmpty()) return null;

        for (int i = 0; i < orders.size(); i++) {
            int rowY = py + HEADER_H + i * ROW_H;
            int btnX = px + PANEL_W - BTN_W - PAD;
            int btnY = rowY + (ROW_H - BTN_H) / 2;

            if (isMouse((int) mx, (int) my, btnX, btnY, BTN_W, BTN_H)) {
                return orders.get(i);
            }
        }
        return null;
    }

    private int totalHeight() {
        int base = HEADER_H + orders.size() * ROW_H + PAD;

        if (PickupSellAutomation.getInstance() != null &&
                PickupSellAutomation.getInstance().isRunning()) {
            base += 12;
        }

        return base;
    }

    private boolean isMouse(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String clamp(String s, int max) {
        return (s != null && s.length() > max)
                ? s.substring(0, max - 1) + "…"
                : s;
    }
}