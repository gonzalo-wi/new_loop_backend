package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StockControlRepository extends JpaRepository<StockControl, UUID>, JpaSpecificationExecutor<StockControl> {
}
