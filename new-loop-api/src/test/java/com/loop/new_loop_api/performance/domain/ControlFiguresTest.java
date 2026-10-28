package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ControlFiguresTest {

    @Test
    void shouldReturnZerosWhenControlHasNoItems() {
        var figures = ControlFigures.of(StockControl.builder().build());

        assertThat(figures.itemsCount()).isZero();
        assertThat(figures.unitsTotal()).isZero();
        assertThat(figures.items()).isEmpty();
    }

    @Test
    void shouldLeaveOutProductsWithoutQuantities() {
        var control = StockControl.builder().build();
        control.getItems().add(item(control, "Bidón 20L", 10, 8, 2));
        control.getItems().add(item(control, "Dispenser", 0, 0, 0));

        var figures = ControlFigures.of(control);

        assertThat(figures.itemsCount()).isEqualTo(1);
        assertThat(figures.items()).extracting(ControlFigures.ItemFigures::productName).containsExactly("Bidón 20L");
    }

    @Test
    void shouldSumTotalUnitsAndKeepRawQuantitiesPerProduct() {
        var control = StockControl.builder().build();
        control.getItems().add(item(control, "Bidón 20L", 10, 8, 2));
        control.getItems().add(item(control, "Bidón 12L", 5, 0, 5));

        var figures = ControlFigures.of(control);

        assertThat(figures.itemsCount()).isEqualTo(2);
        assertThat(figures.unitsTotal()).isEqualTo(15);
        assertThat(figures.items()).extracting(
                        ControlFigures.ItemFigures::totalQuantity,
                        ControlFigures.ItemFigures::fullQuantity,
                        ControlFigures.ItemFigures::exchangeQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10, 8, 2),
                        org.assertj.core.groups.Tuple.tuple(5, 0, 5));
    }

    @Test
    void shouldKeepProductWhenOnlyExchangeHasQuantity() {
        var control = StockControl.builder().build();
        control.getItems().add(item(control, "Bidón 20L", 0, 0, 3));

        assertThat(ControlFigures.of(control).itemsCount()).isEqualTo(1);
    }

    private static StockControlItem item(StockControl control, String productName, int total, int full, int exchange) {
        return StockControlItem.builder()
                .stockControl(control)
                .product(Product.builder().id(UUID.randomUUID()).name(productName).build())
                .totalQuantity(total)
                .fullQuantity(full)
                .exchangeQuantity(exchange)
                .build();
    }
}
