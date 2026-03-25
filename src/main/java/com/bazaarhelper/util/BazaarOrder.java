package com.bazaarhelper.util;

public class BazaarOrder {

    public final String itemName;
    public final String itemId;
    public final long filledAmount;
    public final long totalAmount;
    public final double pricePerUnit;
    public final int chestSlot;

    public BazaarOrder(String itemName, String itemId, long filledAmount, long totalAmount,
                       double pricePerUnit, int chestSlot) {
        this.itemName     = itemName;
        this.itemId       = itemId;
        this.filledAmount = filledAmount;
        this.totalAmount  = totalAmount;
        this.pricePerUnit = pricePerUnit;
        this.chestSlot    = chestSlot;
    }

    public boolean isFullyFilled() {
        return totalAmount > 0 && filledAmount >= totalAmount;
    }
    public boolean hasItemsToClaim() {
        return filledAmount > 0;
    }
    public double totalFilledValue() {
        return filledAmount * pricePerUnit;
    }

    @Override
    public String toString() {
        return String.format("BazaarOrder{item='%s', itemId='%s', filled=%d/%d, price=%.1f, slot=%d}",
                itemName, itemId, filledAmount, totalAmount, pricePerUnit, chestSlot);
    }
}
