package com.loop.new_loop_api.stockcontrols.pdf;

/** How a loaded quantity is printed on the remito: bulk products in bulks, the rest in units. */
public final class RemitoQuantity {

    private RemitoQuantity() {
    }

    /**
     * Drivers load bulk products (e.g. soda crates) by the bulk, so "12" units of a pack of 6 reads as "2".
     * A quantity that doesn't fill whole bulks stays in units with a "u" so it is not read as bulks.
     */
    public static String format(int units, Integer packQuantity) {
        if (packQuantity == null || packQuantity <= 1) return String.valueOf(units);
        if (units % packQuantity == 0) return String.valueOf(units / packQuantity);
        return units + " u";
    }
}
