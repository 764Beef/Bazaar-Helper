package com.bazaarhelper.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BazaarOrderTracker {

    private static final BazaarOrderTracker INSTANCE = new BazaarOrderTracker();
    public  static BazaarOrderTracker getInstance() { return INSTANCE; }
    private BazaarOrderTracker() {}

    private final List<BazaarOrder> orders = new ArrayList<>();

    public void update(List<BazaarOrder> newOrders) {
        orders.clear();
        orders.addAll(newOrders);
    }

    public List<BazaarOrder> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public void clear() { orders.clear(); }
}
