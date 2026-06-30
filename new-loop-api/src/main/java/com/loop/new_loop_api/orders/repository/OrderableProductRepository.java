package com.loop.new_loop_api.orders.repository;

import com.loop.new_loop_api.orders.entity.OrderableProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderableProductRepository extends JpaRepository<OrderableProduct, UUID> {

    boolean existsByCode(String code);
    Page<OrderableProduct> findAllByActive(Boolean active, Pageable pageable);
}
