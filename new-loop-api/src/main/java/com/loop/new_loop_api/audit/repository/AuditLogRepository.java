package com.loop.new_loop_api.audit.repository;

import com.loop.new_loop_api.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
