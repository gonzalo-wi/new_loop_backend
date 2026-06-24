package com.loop.new_loop_api.stockcontrols.entity;

import com.loop.new_loop_api.products.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "stock_control_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockControlItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_control_id", nullable = false)
    private StockControl stockControl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer fullQuantity;

    @Column(nullable = false)
    private Integer exchangeQuantity;

    private Integer differenceQuantity;

    @Column(length = 500)
    private String observations;
}
