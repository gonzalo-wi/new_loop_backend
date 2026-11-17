package com.loop.new_loop_api.loadforecast.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Average per product of what the route actually loaded on its reference days. */
public final class LoadForecastCalculator {

    private static final Comparator<LoadedProduct> PRODUCT_ORDER = Comparator
            .comparing(LoadedProduct::displayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LoadedProduct::name, Comparator.nullsLast(Comparator.naturalOrder()));

    private LoadForecastCalculator() {
    }

    /**
     * Divides by the loads found, not by the weeks looked at: a week without a control is a week without
     * data, not a week the truck went out empty. A product missing from a load counts as 0 for that load.
     */
    public static List<ForecastItem> forecast(List<ReferenceLoad> loads) {
        if (loads.isEmpty()) return List.of();

        Map<UUID, LoadedProduct> products = new LinkedHashMap<>();
        Map<UUID, Integer> totals = new LinkedHashMap<>();
        for (var load : loads) {
            for (var product : load.products()) {
                products.putIfAbsent(product.productId(), product);
                totals.merge(product.productId(), product.quantity(), Integer::sum);
            }
        }

        var divisor = BigDecimal.valueOf(loads.size());
        return products.values().stream()
                .sorted(PRODUCT_ORDER)
                .map(product -> new ForecastItem(product.code(), product.name(),
                        BigDecimal.valueOf(totals.get(product.productId())).divide(divisor, 0, RoundingMode.HALF_UP).intValue()))
                .filter(item -> item.quantity() > 0)
                .toList();
    }
}
