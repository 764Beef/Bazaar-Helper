package com.bazaarhelper.gui;

import com.bazaarhelper.client.BazaarHelperClient;
import com.bazaarhelper.util.BazaarOrder;
import com.bazaarhelper.util.BazaarOrderParser;
import com.bazaarhelper.util.PickupSellAutomation;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;


public class BazaarOverlayManager {

    private static BazaarOrderPanel activePanel = null;
    private static boolean lastLeftMouseDown = false;

    private static final Field FIELD_BG_WIDTH;
    private static final Field FIELD_BG_HEIGHT;

    static {
        Field w = null, h = null;
        for (Class<?> cls = HandledScreen.class; cls != null; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                f.setAccessible(true);
                String n = f.getName();
                if (f.getType() == int.class) {
                    if (n.equals("backgroundWidth")  || n.equals("field_22793")) w = f;
                    if (n.equals("backgroundHeight") || n.equals("field_22794")) h = f;
                }
            }
            if (w != null && h != null) break;
        }
        FIELD_BG_WIDTH  = w;
        FIELD_BG_HEIGHT = h;

        if (FIELD_BG_WIDTH  == null) BazaarHelperClient.LOGGER.warn("[BH] backgroundWidth field not found");
        if (FIELD_BG_HEIGHT == null) BazaarHelperClient.LOGGER.warn("[BH] backgroundHeight field not found");
    }

    private static int getBgWidth(HandledScreen<?> screen) {
        try { return FIELD_BG_WIDTH  != null ? (int) FIELD_BG_WIDTH.get(screen)  : 176; }
        catch (IllegalAccessException e) { return 176; }
    }

    private static int getBgHeight(HandledScreen<?> screen) {
        try { return FIELD_BG_HEIGHT != null ? (int) FIELD_BG_HEIGHT.get(screen) : 222; }
        catch (IllegalAccessException e) { return 222; }
    }

    public static void onScreenOpen(MinecraftClient client, HandledScreen<?> screen) {
        String title = BazaarOrderParser.stripFormatting(screen.getTitle().getString());

        if (!BazaarOrderParser.isBazaarOrderScreen(title)) {
            activePanel = null;
            return;
        }

        BazaarHelperClient.LOGGER.info("[BH] Bazaar order screen: '{}'", title);
        ScreenHandler handler = screen.getScreenHandler();
        lastLeftMouseDown = false;

        rebuildPanel(screen, handler, title);

        ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> {
            if (activePanel != null) {
                activePanel.render(ctx, client.textRenderer, mx, my);

                boolean leftMouseDown = GLFW.glfwGetMouseButton(
                        client.getWindow().getHandle(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT
                ) == GLFW.GLFW_PRESS;

                if (leftMouseDown && !lastLeftMouseDown) {
                    BazaarOrder clicked = activePanel.onMouseClick(mx, my);
                    PickupSellAutomation automation = PickupSellAutomation.getInstance();
                    if (clicked != null && automation != null && !automation.isRunning()) {
                        BazaarHelperClient.LOGGER.info("[BH] Button clicked for: {}", clicked);
                        automation.start(clicked);
                    }
                }

                lastLeftMouseDown = leftMouseDown;
            }
        });


        final int[] tickCounter = {0};
        ScreenEvents.afterTick(screen).register(scr -> {
            tickCounter[0]++;
            if (tickCounter[0] >= 20) {
                tickCounter[0] = 0;
                rebuildPanel(screen, handler, title);
            }
        });
    }

    private static void rebuildPanel(HandledScreen<?> screen,
                                     ScreenHandler handler, String title) {
        List<BazaarOrder> orders = BazaarOrderParser.parseOrders(handler, title);

        int guiW = getBgWidth(screen);
        int guiH = getBgHeight(screen);
        int scrX = (screen.width  - guiW) / 2;
        int scrY = (screen.height - guiH) / 2;

        activePanel = new BazaarOrderPanel(
                orders, scrX, scrY, guiW, guiH,
                screen.width, screen.height
        );
    }

    public static void clear() {
        activePanel = null;
        lastLeftMouseDown = false;
    }
}
