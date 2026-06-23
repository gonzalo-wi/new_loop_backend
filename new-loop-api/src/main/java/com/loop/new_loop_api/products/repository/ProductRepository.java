package com.loop.new_loop_api.products.repository;

import com.loop.new_loop_api.products.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByCode(String code);
}
