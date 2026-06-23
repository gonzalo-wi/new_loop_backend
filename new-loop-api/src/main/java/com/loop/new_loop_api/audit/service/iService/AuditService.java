package com.loop.new_loop_api.audit.service.iService;

import com.loop.new_loop_api.audit.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditService {

    void register(String action, String entityName, UUID entityId, Object oldValue, Object newValue);

    Page<AuditLogResponse> getAll(String entityName, String action, UUID entityId,
                                  LocalDateTime from, LocalDateTime to, Pageable pageable);
}
