package com.loop.new_loop_api.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID   userId;
    private String userRole;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 100)
    private String entityName;

    private UUID entityId;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = "ADMIN_WEB";

    @Column(length = 45)
    private String ipAddress;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
