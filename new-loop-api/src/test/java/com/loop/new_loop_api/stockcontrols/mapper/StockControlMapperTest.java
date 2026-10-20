package com.loop.new_loop_api.stockcontrols.mapper;

import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockControlMapperTest {

    private final StockControlMapper stockControlMapper = new StockControlMapper();

    private static CreateStockControlItemRequest itemRequest(int total, int full) {
        var request = new CreateStockControlItemRequest();
        request.setTotalQuantity(total);
        request.setFullQuantity(full);
        request.setExchangeQuantity(1);
        return request;
    }

    private static StockControl control(ControlType type) {
        return StockControl.builder().type(type).build();
    }

    @Test
    void should_forceFullToTotal_when_entryProductIsFullEqualsTotal() {
        var pallet = Product.builder().code("PM").fullEqualsTotal(true).build();

        var item = stockControlMapper.toItem(itemRequest(12, 3), pallet, control(ControlType.ENTRY));

        assertThat(item.getTotalQuantity()).isEqualTo(12);
        assertThat(item.getFullQuantity()).isEqualTo(12);
        assertThat(item.getExchangeQuantity()).isEqualTo(1);
    }

    @Test
    void should_keepRequestedFull_when_productTracksFullSeparately() {
        var bottle = Product.builder().code("B20").fullEqualsTotal(false).build();

        var item = stockControlMapper.toItem(itemRequest(12, 3), bottle, control(ControlType.ENTRY));

        assertThat(item.getFullQuantity()).isEqualTo(3);
    }

    @Test
    void should_keepRequestedFull_when_exitControlHasFullEqualsTotalProduct() {
        var pallet = Product.builder().code("PM").fullEqualsTotal(true).build();

        var item = stockControlMapper.toItem(itemRequest(12, 0), pallet, control(ControlType.EXIT));

        assertThat(item.getFullQuantity()).isZero();
    }
}
