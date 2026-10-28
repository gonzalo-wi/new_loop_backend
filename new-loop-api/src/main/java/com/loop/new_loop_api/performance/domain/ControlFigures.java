package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;

import java.util.List;
import java.util.UUID;

/**
 * The figures of a stock control that metrics care about. Every control carries the whole catalog
 * (missing products are added with zeros), so only products with some quantity count as items.
 */
public record ControlFigures(int itemsCount, int unitsTotal, List<ItemFigures> items) {

    public record ItemFigures(UUID productId, String productName,
                              int totalQuantity, int fullQuantity, int exchangeQuantity) {}

    public static ControlFigures of(StockControl control) {
        var items = control.getItems().stream()
                .filter(ControlFigures::hasQuantities)
                .map(ControlFigures::toItemFigures)
                .toList();
        var unitsTotal = items.stream().mapToInt(ItemFigures::totalQuantity).sum();
        return new ControlFigures(items.size(), unitsTotal, items);
    }

    private static boolean hasQuantities(StockControlItem item) {
        return valueOf(item.getTotalQuantity()) != 0
                || valueOf(item.getFullQuantity()) != 0
                || valueOf(item.getExchangeQuantity()) != 0;
    }

    private static ItemFigures toItemFigures(StockControlItem item) {
        return new ItemFigures(item.getProduct().getId(), item.getProduct().getName(),
                valueOf(item.getTotalQuantity()), valueOf(item.getFullQuantity()), valueOf(item.getExchangeQuantity()));
    }

    private static int valueOf(Integer quantity) {
        return quantity != null ? quantity : 0;
    }
}
