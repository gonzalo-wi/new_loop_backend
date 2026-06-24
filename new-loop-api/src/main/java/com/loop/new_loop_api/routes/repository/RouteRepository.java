package com.loop.new_loop_api.routes.repository;

import com.loop.new_loop_api.routes.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    boolean existsByCode(String code);
}
