package com.loop.new_loop_api.audit.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AuditLogResponse {

    private UUID          id;
    private UUID          userId;
    private String        username;
    private String        userRole;
    private String        action;
    private String        entityName;
    private UUID          entityId;
    private String        oldValue;
    private String        newValue;
    private String        reason;
    private String        source;
    private String        ipAddress;
    private LocalDateTime createdAt;
}
