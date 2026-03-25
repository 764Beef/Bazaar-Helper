package com.bazaarhelper.util;

import com.bazaarhelper.client.BazaarHelperClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickupSellAutomation {

    private enum SellActionResult {
        ACTION_TAKEN,
        DONE,
        WAITING_FOR_SPACE,
        NO_CLAIMED_ITEMS,
        ERROR
    }

    public enum State {
        IDLE,
        CLICK_ORDER_SLOT,
        WAIT_FOR_MANAGE,
        CLICK_CLAIM,
        CLOSE_MANAGE,
        OPEN_SB_MENU,
        WAIT_FOR_SB_MENU,
        CLICK_COOKIE,
        WAIT_FOR_COOKIE,
        CLICK_SELL,
        OPEN_BAZAAR,
        WAIT_FOR_BAZAAR,
        CLICK_MANAGE_ORDERS,
        WAIT_FOR_BAZAAR_ORDERS,
        DONE
    }

    private static final String[] CLAIM_KEYWORDS    = { "Claim", "Collect", "Collect All", "Claim All", "Pickup All" };
    private static final int HOTBAR_SKYBLOCK_SLOT = 8; // SkyBlock Menu = slot 9 in-game = index 8 (0-based)
    private static final int COOKIE_MENU_SLOT     = 51;
    private static final int SELL_ORDER_SLOT      = 49;
    private static final int MANAGE_ORDERS_TAB_SLOT = 50;
    private static final String BAZAAR_COMMAND = "bz";
    private static final int TICK_DELAY_SHORT     = 4;
    private static final int TICK_DELAY_MEDIUM    = 8;
    private static final int TICK_DELAY_LONG      = 16;
    private static final int TICK_DELAY_SELL_NEXT = 20;
    private static final int MAX_WAIT_TICKS       = 100; // ~5 seconds before giving up on a state
    private static final int MAX_CYCLES           = 40;  // safety cap on full iterations
    private static final Pattern SELL_VALUE_PATTERN =
            Pattern.compile("(?:sell|value|worth|receive|earn|price).*?([\\d.,]+[kmb]?)\\s+coins",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS_PATTERN =
            Pattern.compile("([\\d.,]+[kmb]?)\\s+coins", Pattern.CASE_INSENSITIVE);


    private State state          = State.IDLE;
    private BazaarOrder order    = null;
    private int tickDelay        = 0;
    private int waitedTicks      = 0;
    private int cycles           = 0;
    private long totalClaimedUnits = 0;
    private double totalClaimCost = 0;
    private double totalSoldCoins = 0;
    private List<ItemStack> preClaimInventory = List.of();
    private List<Integer> claimedInventorySlots = new ArrayList<>();

    private static final PickupSellAutomation INSTANCE = new PickupSellAutomation();
    public  static PickupSellAutomation getInstance() { return INSTANCE; }
    private PickupSellAutomation() {}


    public boolean isRunning() { return state != State.IDLE && state != State.DONE; }
    public State   getState()  { return state; }

    public void start(BazaarOrder targetOrder) {
        if (isRunning()) {
            chat("§c[BH] Already running — please wait.");
            return;
        }
        this.order  = targetOrder;
        this.cycles = 0;
        this.totalClaimedUnits = 0;
        this.totalClaimCost = 0;
        this.totalSoldCoins = 0;
        this.preClaimInventory = List.of();
        this.claimedInventorySlots = new ArrayList<>();
        transitionTo(State.CLICK_ORDER_SLOT, TICK_DELAY_SHORT);
        chat("§a[BH] §fStarting Pickup & Sell for §e" + order.itemName);
    }

    public void cancel() {
        chat("§c[BH] Cancelled.");
        reset();
    }


    public void tick() {
        if (state == State.IDLE || state == State.DONE) return;

        if (tickDelay > 0) { tickDelay--; return; }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            abort("Player disconnected");
            return;
        }

        if (cycles > MAX_CYCLES) {
            abort("Safety limit reached (" + MAX_CYCLES + " cycles)");
            return;
        }

        waitedTicks++;

        String title = currentTitle(mc);
        ScreenHandler handler = mc.player.currentScreenHandler;

        switch (state) {
            case CLICK_ORDER_SLOT -> {
                if (BazaarOrderParser.isBazaarOrderScreen(title)) {
                    capturePreClaimInventory(mc);
                    clickSlot(mc, handler, order.chestSlot);
                    transitionTo(State.WAIT_FOR_MANAGE, TICK_DELAY_MEDIUM);
                } else {
                    if (waitedTicks > MAX_WAIT_TICKS) abort("Bazaar order screen never appeared");
                }
            }

            case WAIT_FOR_MANAGE -> {
                if (BazaarOrderParser.isBazaarManageOrderScreen(title)) {
                    transitionTo(State.CLICK_CLAIM, TICK_DELAY_SHORT);
                } else if (didClaimDirectly(handler, title)) {
                    recordClaimedInventory(mc);
                    closeCurrentScreen(mc);
                    transitionTo(State.OPEN_SB_MENU, TICK_DELAY_MEDIUM);
                } else if (BazaarOrderParser.isBazaarOrderScreen(title) && waitedTicks >= TICK_DELAY_LONG) {
                    BazaarHelperClient.LOGGER.info("[BH] No manage screen appeared; treating current Bazaar screen as direct-claim flow");
                    recordClaimedInventory(mc);
                    closeCurrentScreen(mc);
                    transitionTo(State.OPEN_SB_MENU, TICK_DELAY_MEDIUM);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    abort("Manage Order sub-menu never appeared");
                }
            }

            case CLICK_CLAIM -> {
                int slot = findSlot(handler, CLAIM_KEYWORDS);
                if (slot >= 0) {
                    capturePreClaimInventory(mc);
                    clickSlot(mc, handler, slot);
                    transitionTo(State.CLOSE_MANAGE, TICK_DELAY_LONG);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    abort("Could not find Claim button (slot names: " + dumpSlotNames(handler) + ")");
                }
            }

            case CLOSE_MANAGE -> {
                closeCurrentScreen(mc);
                recordClaimedInventory(mc);
                transitionTo(State.OPEN_SB_MENU, TICK_DELAY_LONG);
            }

            case OPEN_SB_MENU -> {
                openSkyblockMenu(mc);
                transitionTo(State.WAIT_FOR_SB_MENU, TICK_DELAY_LONG);
            }

            case WAIT_FOR_SB_MENU -> {
                if (BazaarOrderParser.isSkyblockMenu(title) || isLikelySkyblockMenu(handler, title)) {
                    transitionTo(State.CLICK_COOKIE, TICK_DELAY_SHORT);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    waitedTicks = 0;
                    openSkyblockMenu(mc);
                    tickDelay = TICK_DELAY_LONG;
                    cycles++;
                }
            }

            case CLICK_COOKIE -> {
                if (hasSlot(handler, COOKIE_MENU_SLOT)) {
                    clickSlot(mc, handler, COOKIE_MENU_SLOT);
                    transitionTo(State.WAIT_FOR_COOKIE, TICK_DELAY_LONG);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    abort("Cookie slot 51 not available in SkyBlock Menu (slots: " + dumpSlotNames(handler) + ")");
                }
            }

            case WAIT_FOR_COOKIE -> {
                if (BazaarOrderParser.isBoosterCookieScreen(title) || isLikelyCookieMenu(handler, title)) {
                    transitionTo(State.CLICK_SELL, TICK_DELAY_SHORT);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    abort("Booster Cookie menu never appeared (slots: " + dumpSlotNames(handler) + ")");
                }
            }

            case CLICK_SELL -> {
                if (!hasSlot(handler, SELL_ORDER_SLOT)) {
                    if (waitedTicks > MAX_WAIT_TICKS) {
                        abort("Sell slot 49 not available in Cookie menu (slots: " + dumpSlotNames(handler) + ")");
                    }
                    break;
                }

                SellActionResult result = sellNextMatchingStack(mc, handler);
                if (result == SellActionResult.ACTION_TAKEN) {
                    tickDelay = TICK_DELAY_SELL_NEXT;
                    waitedTicks = 0;
                } else if (result == SellActionResult.DONE) {
                    cycles++;
                    transitionTo(State.OPEN_BAZAAR, TICK_DELAY_LONG);
                } else if (result == SellActionResult.NO_CLAIMED_ITEMS) {
                    abort("Claim produced no new inventory slots. Clear inventory space before claiming.");
                } else if (result == SellActionResult.WAITING_FOR_SPACE) {
                    if (waitedTicks > MAX_WAIT_TICKS) {
                        abort("No empty hotbar slot available to sell claimed items");
                    }
                } else {
                    abort("Failed while selling claimed items");
                }
            }

            case OPEN_BAZAAR -> {
                openBazaar(mc);
                transitionTo(State.WAIT_FOR_BAZAAR, TICK_DELAY_LONG);
            }

            case WAIT_FOR_BAZAAR -> {
                if (BazaarOrderParser.isBazaarScreen(title)) {
                    transitionTo(State.CLICK_MANAGE_ORDERS, TICK_DELAY_SHORT);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    openBazaar(mc);
                    tickDelay = TICK_DELAY_LONG;
                    waitedTicks = 0;
                }
            }

            case CLICK_MANAGE_ORDERS -> {
                if (hasSlot(handler, MANAGE_ORDERS_TAB_SLOT)) {
                    clickSlot(mc, handler, MANAGE_ORDERS_TAB_SLOT);
                    transitionTo(State.WAIT_FOR_BAZAAR_ORDERS, TICK_DELAY_LONG);
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    abort("Manage Orders slot 50 not available in Bazaar menu (slots: " + dumpSlotNames(handler) + ")");
                }
            }

            case WAIT_FOR_BAZAAR_ORDERS -> {
                if (BazaarOrderParser.isBazaarOrderScreen(title)) {
                    BazaarOrder currentOrder = readTrackedOrder(handler);
                    if (currentOrder == null || !currentOrder.hasItemsToClaim()) {
                        chat("§a[BH] §fClaim successful §8| §7Profit: §6" + formatSignedCoins(totalProfit()) + " coins");
                        transitionTo(State.DONE, TICK_DELAY_SHORT);
                        onCycleComplete(mc);
                    } else {
                        this.order = currentOrder;
                        transitionTo(State.CLICK_ORDER_SLOT, TICK_DELAY_SHORT);
                    }
                } else if (waitedTicks > MAX_WAIT_TICKS) {
                    openBazaar(mc);
                    transitionTo(State.WAIT_FOR_BAZAAR, TICK_DELAY_LONG);
                }
            }
        }
    }

    private void openSkyblockMenu(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;

        mc.player.getInventory().setSelectedSlot(HOTBAR_SKYBLOCK_SLOT);

        ItemStack held = mc.player.getInventory().getStack(HOTBAR_SKYBLOCK_SLOT);
        BazaarHelperClient.LOGGER.info("[BH] Hotbar slot 8 item: '{}'",
                BazaarOrderParser.stripFormatting(held.getName().getString()));

        mc.interactionManager.interactItem(
                mc.player,
                net.minecraft.util.Hand.MAIN_HAND
        );
    }

    private void openBazaar(MinecraftClient mc) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        BazaarHelperClient.LOGGER.info("[BH] Sending /{} to open Bazaar", BAZAAR_COMMAND);
        mc.player.networkHandler.sendChatCommand(BAZAAR_COMMAND);
    }

    private void closeCurrentScreen(MinecraftClient mc) {
        if (mc.player == null) return;
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
    }

    private void clickSlot(MinecraftClient mc, ScreenHandler handler, int slot) {
        if (slot < 0 || slot >= handler.slots.size()) {
            BazaarHelperClient.LOGGER.warn("[BH] Tried to click invalid slot {}", slot);
            return;
        }
        BazaarHelperClient.LOGGER.info("[BH] Clicking slot {} ({})", slot,
                BazaarOrderParser.stripFormatting(handler.slots.get(slot).getStack().getName().getString()));
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private SellActionResult sellNextMatchingStack(MinecraftClient mc, ScreenHandler handler) {
        if (mc.player == null || mc.interactionManager == null) {
            return SellActionResult.ERROR;
        }
        if (claimedInventorySlots.isEmpty()) {
            return SellActionResult.DONE;
        }

        int playerInvStart = getPlayerInventoryStart(handler);
        if (playerInvStart < 0) {
            BazaarHelperClient.LOGGER.warn("[BH] Could not locate player inventory section in Cookie menu");
            return SellActionResult.ERROR;
        }

        List<Integer> remainingClaimedSlots = new ArrayList<>();

        for (int playerSlot : claimedInventorySlots) {
            int handlerSlot = toHandlerPlayerSlot(playerInvStart, playerSlot);
            if (handlerSlot < playerInvStart || handlerSlot >= handler.slots.size()) {
                continue;
            }

            ItemStack stack = handler.slots.get(handlerSlot).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            remainingClaimedSlots.add(playerSlot);
        }

        for (int i = 0; i < remainingClaimedSlots.size(); i++) {
            int playerSlot = remainingClaimedSlots.get(i);
            int handlerSlot = toHandlerPlayerSlot(playerInvStart, playerSlot);
            if (handlerSlot < playerInvStart || handlerSlot >= handler.slots.size()) {
                continue;
            }

            ItemStack stack = handler.slots.get(handlerSlot).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            double projectedSellCoins = readProjectedSellCoins(handler);
            if (projectedSellCoins > 0) {
                totalSoldCoins += projectedSellCoins;
                BazaarHelperClient.LOGGER.info("[BH] Recorded projected sell proceeds from slot 49: {} coins (running total: {})",
                        projectedSellCoins, totalSoldCoins);
            } else {
                BazaarHelperClient.LOGGER.warn("[BH] Could not read projected sell proceeds from slot 49 before selling slot {}", handlerSlot);
            }
            BazaarHelperClient.LOGGER.info("[BH] Selling claimed stack from slot {} with direct click", handlerSlot);
            mc.interactionManager.clickSlot(handler.syncId, handlerSlot, 0, SlotActionType.PICKUP, mc.player);
            remainingClaimedSlots.remove(i);
            claimedInventorySlots = remainingClaimedSlots;
            return SellActionResult.ACTION_TAKEN;
        }

        claimedInventorySlots = remainingClaimedSlots;
        return SellActionResult.DONE;
    }

    private int getPlayerInventoryStart(ScreenHandler handler) {
        int start = handler.slots.size() - 36;
        return start >= 0 ? start : -1;
    }

    private int toHandlerPlayerSlot(int playerInvStart, int playerSlot) {
        if (playerSlot >= 0 && playerSlot < 9) {
            return playerInvStart + 27 + playerSlot;
        }
        return playerInvStart + (playerSlot - 9);
    }

    private void capturePreClaimInventory(MinecraftClient mc) {
        if (mc.player == null) {
            preClaimInventory = List.of();
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        List<ItemStack> snapshot = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            snapshot.add(inventory.getStack(slot).copy());
        }
        preClaimInventory = snapshot;
    }

    private void recordClaimedInventory(MinecraftClient mc) {
        claimedInventorySlots = new ArrayList<>();
        if (mc.player == null || preClaimInventory.isEmpty()) {
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        boolean mergedIntoExistingStack = false;
        long claimedUnitsThisCycle = 0;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack before = slot < preClaimInventory.size() ? preClaimInventory.get(slot) : ItemStack.EMPTY;
            ItemStack after = inventory.getStack(slot);

            if (before.isEmpty() && !after.isEmpty()) {
                claimedInventorySlots.add(slot);
                claimedUnitsThisCycle += after.getCount();
                continue;
            }

            if (!before.isEmpty() && !after.isEmpty()) {
                if (ItemStack.areItemsAndComponentsEqual(before, after) && after.getCount() > before.getCount()) {
                    claimedUnitsThisCycle += (after.getCount() - before.getCount());
                    mergedIntoExistingStack = true;
                } else if (!ItemStack.areItemsAndComponentsEqual(before, after)) {
                    mergedIntoExistingStack = true;
                }
            }
        }

        BazaarHelperClient.LOGGER.info("[BH] Claimed inventory slots: {}", claimedInventorySlots);
        recordClaimForCurrentCycle(claimedUnitsThisCycle);
        if (mergedIntoExistingStack) {
            BazaarHelperClient.LOGGER.warn("[BH] Claim merged into an existing stack; refusing to sell pre-existing inventory items");
        }
    }

    private void recordClaimForCurrentCycle(long claimedUnitsThisCycle) {
        if (order == null || claimedUnitsThisCycle <= 0) {
            return;
        }

        totalClaimedUnits += claimedUnitsThisCycle;
        totalClaimCost += claimedUnitsThisCycle * order.pricePerUnit;
    }

    private int findSlot(ScreenHandler handler, String... keywords) {
        for (int i = 0; i < handler.slots.size(); i++) {
            String name = BazaarOrderParser.stripFormatting(
                    handler.slots.get(i).getStack().getName().getString()).toLowerCase();
            for (String keyword : keywords) {
                if (name.contains(keyword.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean hasSlot(ScreenHandler handler, int slot) {
        return slot >= 0 && slot < handler.slots.size();
    }

    private boolean didClaimDirectly(ScreenHandler handler, String title) {
        if (!BazaarOrderParser.isBazaarOrderScreen(title) || !hasSlot(handler, order.chestSlot)) {
            return false;
        }

        BazaarOrder currentOrder = readTrackedOrder(handler);

        return currentOrder != null;
    }


    private BazaarOrder readTrackedOrder(ScreenHandler handler) {
        if (order == null || !hasSlot(handler, order.chestSlot)) {
            return null;
        }

        return BazaarOrderParser.tryParseSlot(
                handler.slots.get(order.chestSlot).getStack(),
                order.chestSlot
        );
    }

    private boolean isLikelySkyblockMenu(ScreenHandler handler, String title) {
        return mcScreenOpen(title) && hasSlot(handler, COOKIE_MENU_SLOT);
    }

    private boolean isLikelyCookieMenu(ScreenHandler handler, String title) {
        return mcScreenOpen(title) && hasSlot(handler, SELL_ORDER_SLOT);
    }

    private boolean mcScreenOpen(String title) {
        return title != null && !title.isEmpty();
    }


    private void transitionTo(State next, int delay) {
        BazaarHelperClient.LOGGER.info("[BH] {} → {}", state, next);
        state       = next;
        tickDelay   = delay;
        waitedTicks = 0;
    }

    private void onCycleComplete(MinecraftClient mc) {
        if (mc.player == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("  §6§lBazaar Helper §8| §aOrder Flip Done!"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("  §7Item: §f" + order.itemName));
        mc.inGameHud.getChatHud().addMessage(Text.literal("  §7Claimed: §e" + BazaarOrderParser.formatNumber(totalClaimedUnits) + " units"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("  §7Cost:   §6" + BazaarOrderParser.formatCoins(totalClaimCost) + " coins"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("  §7Profit: §a" + formatSignedCoins(totalProfit()) + " coins"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    public void onChatMessage(String message) {
    }

    public void abort(String reason) {
        BazaarHelperClient.LOGGER.error("[BH] Aborted: {}", reason);
        chat("§c[BH] Aborted — " + reason);
        reset();
    }

    private void reset() {
        state       = State.IDLE;
        order       = null;
        tickDelay   = 0;
        waitedTicks = 0;
        cycles      = 0;
        totalClaimedUnits = 0;
        totalClaimCost = 0;
        totalSoldCoins = 0;
        preClaimInventory = List.of();
        claimedInventorySlots = new ArrayList<>();
    }

    private double totalProfit() {
        return totalSoldCoins - totalClaimCost;
    }

    private String formatSignedCoins(double coins) {
        String prefix = coins >= 0 ? "+" : "-";
        return prefix + BazaarOrderParser.formatCoins(Math.abs(coins));
    }

    private double readProjectedSellCoins(ScreenHandler handler) {
        if (!hasSlot(handler, SELL_ORDER_SLOT)) {
            return 0;
        }

        ItemStack sellStack = handler.slots.get(SELL_ORDER_SLOT).getStack();
        if (sellStack.isEmpty()) {
            return 0;
        }

        double matchedCoins = 0;
        for (String rawLine : BazaarOrderParser.getLoreLines(sellStack)) {
            String line = BazaarOrderParser.stripFormatting(rawLine);

            Matcher sellValueMatcher = SELL_VALUE_PATTERN.matcher(line);
            if (sellValueMatcher.find()) {
                matchedCoins = Math.max(matchedCoins, BazaarOrderParser.parseLong(sellValueMatcher.group(1)));
                continue;
            }

            Matcher genericCoinsMatcher = COINS_PATTERN.matcher(line);
            if (genericCoinsMatcher.find()) {
                matchedCoins = Math.max(matchedCoins, BazaarOrderParser.parseLong(genericCoinsMatcher.group(1)));
            }
        }

        return matchedCoins;
    }


    private String currentTitle(MinecraftClient mc) {
        if (mc.currentScreen == null) return "";
        return BazaarOrderParser.stripFormatting(mc.currentScreen.getTitle().getString());
    }

    private void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal(msg));
    }

    private String dumpSlotNames(ScreenHandler handler) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            String name = BazaarOrderParser.stripFormatting(
                    handler.slots.get(i).getStack().getName().getString());
            if (!name.isEmpty() && !name.equals("air")) sb.append(i).append(":").append(name).append(" | ");
        }
        return sb.toString();
    }
}
