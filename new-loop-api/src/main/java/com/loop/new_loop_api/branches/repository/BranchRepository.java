package com.loop.new_loop_api.branches.repository;

import com.loop.new_loop_api.branches.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    boolean existsByCode(String code);
}
