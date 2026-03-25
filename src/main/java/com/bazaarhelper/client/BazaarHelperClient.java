package com.bazaarhelper.client;

import com.bazaarhelper.gui.BazaarOverlayManager;
import com.bazaarhelper.util.PickupSellAutomation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BazaarHelperClient implements ClientModInitializer {

    public static final String MOD_ID = "bazaarhelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Bazaar Helper] Initializing...");

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                BazaarOverlayManager.onScreenOpen(client, handledScreen);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PickupSellAutomation.getInstance().tick();
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            PickupSellAutomation.getInstance().onChatMessage(message.getString());
        });

        LOGGER.info("[Bazaar Helper] Ready.");
    }
}
