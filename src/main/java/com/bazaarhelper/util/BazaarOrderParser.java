package com.bazaarhelper.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BazaarOrderParser {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:Amount|Filled|Claimable|Items to Claim):\\s*([\\d.,]+[kmb]?)\\s*/\\s*([\\d.,]+[kmb]?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERIC_AMOUNT_PATTERN =
            Pattern.compile("([\\d.,]+[kmb]?)\\s*/\\s*([\\d.,]+[kmb]?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("Price per unit:\\s*([\\d,.]+)\\s*coins");

    private static final Pattern TEXT_FIELD_PATTERN =
            Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    public static List<BazaarOrder> parseOrders(ScreenHandler handler, String screenTitle) {
        List<BazaarOrder> result = new ArrayList<>();
        if (!isBazaarOrderScreen(screenTitle)) {
            return result;
        }

        int max = Math.min(handler.slots.size(), 45);
        for (int slot = 0; slot < max; slot++) {
            ItemStack stack = handler.slots.get(slot).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            BazaarOrder order = tryParseSlot(stack, slot);
            if (order != null && order.hasItemsToClaim()) {
                result.add(order);
            }
        }
        return result;
    }

    public static BazaarOrder tryParseSlot(ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return null;
        }

        List<String> loreLines = getRawLore(stack);
        if (loreLines.isEmpty()) {
            return null;
        }

        String itemName = stripFormatting(stack.getName().getString());
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        long filled = -1;
        long total = -1;
        double price = 0;

        for (String line : loreLines) {
            String plain = stripFormatting(line);

            Matcher amountMatcher = AMOUNT_PATTERN.matcher(plain);
            if (amountMatcher.find()) {
                filled = parseLong(amountMatcher.group(1));
                total = parseLong(amountMatcher.group(2));
            } else {
                Matcher genericAmountMatcher = GENERIC_AMOUNT_PATTERN.matcher(plain);
                if (genericAmountMatcher.find()) {
                    filled = parseLong(genericAmountMatcher.group(1));
                    total = parseLong(genericAmountMatcher.group(2));
                }
            }

            Matcher priceMatcher = PRICE_PATTERN.matcher(plain);
            if (priceMatcher.find()) {
                price = parseDouble(priceMatcher.group(1));
            }
        }

        if (filled < 0 || total <= 0) {
            return null;
        }

        return new BazaarOrder(itemName, itemId, filled, total, price, slot);
    }

    public static boolean isBazaarScreen(String title) {
        return title.contains("Bazaar") || title.contains("Co-op Bazaar");
    }

    public static boolean isBazaarOrderScreen(String title) {
        return title.contains("Your Bazaar Orders")
                || title.contains("Manage Orders")
                || title.contains("Bazaar Orders");
    }

    public static boolean isBazaarManageOrderScreen(String title) {
        return title.contains("Manage Order") || title.contains("Collect Items");
    }

    public static boolean isBoosterCookieScreen(String title) {
        return title.contains("Booster Cookie") || title.contains("Cookie Menu");
    }

    public static boolean isSkyblockMenu(String title) {
        return title.equals("SkyBlock Menu") || title.contains("Sky Block Menu");
    }

    public static List<String> getLoreLines(ItemStack stack) {
        return getRawLore(stack);
    }

    private static List<String> getRawLore(ItemStack stack) {
        List<String> lines = new ArrayList<>();

        try {
            var loreComponent = stack.get(DataComponentTypes.LORE);
            if (loreComponent != null) {
                for (Text text : loreComponent.lines()) {
                    lines.add(text.getString());
                }
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound customDataNbt = customData != null ? customData.copyNbt() : null;
            if (customDataNbt == null || !customDataNbt.contains("display")) {
                return lines;
            }

            NbtCompound display = customDataNbt.getCompound("display").orElse(null);
            if (display == null) {
                return lines;
            }
            if (!display.contains("Lore")) {
                return lines;
            }

            NbtList loreList = display.getList("Lore").orElse(null);
            if (loreList == null) {
                return lines;
            }
            for (int i = 0; i < loreList.size(); i++) {
                String raw = loreList.getString(i).orElse("");
                lines.add(extractTextFromJson(raw));
            }
        } catch (Exception ignored) {
        }

        return lines;
    }

    private static String extractTextFromJson(String raw) {
        Matcher matcher = TEXT_FIELD_PATTERN.matcher(raw);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            builder.append(unescapeJson(matcher.group(1)));
        }

        String extracted = builder.toString().trim();
        return extracted.isEmpty() ? raw : extracted;
    }

    private static String unescapeJson(String text) {
        return text
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "")
                .replace("\\r", "")
                .replace("\\t", " ");
    }

    public static String stripFormatting(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("[\\u00A7&][0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static long parseLong(String s) {
        try {
            String normalized = s.trim().toLowerCase().replace(",", "");
            long multiplier = 1L;

            if (normalized.endsWith("k")) {
                multiplier = 1_000L;
                normalized = normalized.substring(0, normalized.length() - 1);
            } else if (normalized.endsWith("m")) {
                multiplier = 1_000_000L;
                normalized = normalized.substring(0, normalized.length() - 1);
            } else if (normalized.endsWith("b")) {
                multiplier = 1_000_000_000L;
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            return Math.round(Double.parseDouble(normalized) * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String formatNumber(long n) {
        return String.format("%,d", n);
    }

    public static String formatCoins(double coins) {
        if (coins >= 1_000_000) {
            return String.format("%.2fM", coins / 1_000_000);
        }
        if (coins >= 1_000) {
            return String.format("%.1fk", coins / 1_000);
        }
        return String.format("%.0f", coins);
    }
}
