package com.loop.new_loop_api.performance.entity;

import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One business fact for operational metrics. Append-only: the table rejects updates and deletes
 * (V40), and usernames and the route code are snapshots taken when the fact happened.
 */
@Entity
@Immutable
@Table(name = "operation_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OperationEventType eventType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationEventSubject subjectType;

    @Column(nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ControlType controlType;

    private UUID branchId;

    private UUID routeId;

    @Column(length = 100)
    private String routeCode;

    private UUID actorUserId;

    @Column(length = 100)
    private String actorUsername;

    private UUID responsibleUserId;

    @Column(length = 100)
    private String responsibleUsername;

    private Integer itemsCount;

    private Integer unitsTotal;

    private Integer rejectionRound;

    private Long responseSeconds;

    @Column(nullable = false)
    @Builder.Default
    private boolean resubmission = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IntegrationName integration;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private boolean imported = false;

    private UUID sourceAuditId;

    @OneToMany(mappedBy = "event", cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<OperationEventItem> items = new ArrayList<>();
}
