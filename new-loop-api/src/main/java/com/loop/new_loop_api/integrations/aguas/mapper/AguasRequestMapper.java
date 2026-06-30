package com.loop.new_loop_api.integrations.aguas.mapper;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEntryProduct;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEntryRequest;
import com.loop.new_loop_api.integrations.aguas.dto.AguasExitProduct;
import com.loop.new_loop_api.integrations.aguas.dto.AguasExitRequest;
import com.loop.new_loop_api.integrations.aguas.dto.AguasSucursal;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.stereotype.Component;

@Component
public class AguasRequestMapper {

    public AguasEntryRequest toEntryRequest(StockControl control, String supervisorUsername) {
        var products = control.getItems().stream()
                .map(item -> AguasEntryProduct.builder()
                        .productId(item.getProduct().getCode())
                        .total(item.getTotalQuantity())
                        .filled(item.getFullQuantity())
                        .recharge(item.getExchangeQuantity())
                        .build())
                .toList();

        return AguasEntryRequest.builder()
                .products(products)
                .deliveryId(parseInteger(control.getRoute().getCode()))
                .supervisor(supervisorUsername)
                .comments(control.getObservations())
                .build();
    }

    public AguasExitRequest toExitRequest(StockControl control) {
        var products = control.getItems().stream()
                .map(item -> AguasExitProduct.builder()
                        .productId(item.getProduct().getCode())
                        .total(item.getTotalQuantity())
                        .build())
                .toList();

        return AguasExitRequest.builder()
                .products(products)
                .deliveryId(parseInteger(control.getRoute().getCode()))
                .date(control.getControlDate().toString())
                .sucursal(toSucursal(control.getBranch()))
                .comments(control.getObservations())
                .build();
    }

    private AguasSucursal toSucursal(Branch branch) {
        return AguasSucursal.builder()
                .codigo(branch.getCode())
                .direccion(branch.getAddress())
                .localidad(branch.getLocality())
                .cndiva(parseInteger(branch.getVatCondition()))
                .nrocuit(branch.getCuit() != null ? branch.getCuit() : "")
                .build();
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
