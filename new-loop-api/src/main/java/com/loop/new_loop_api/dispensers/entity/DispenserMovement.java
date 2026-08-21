package com.loop.new_loop_api.dispensers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dispenser_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispenserMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DispenserMovementType type;

    @Column(name = "route_code", nullable = false, length = 50)
    private String routeCode;

    @Column(nullable = false)
    private String technician;

    @Column(name = "location_id", nullable = false)
    private Integer locationId;

    @Column(name = "state_id", nullable = false)
    private Integer stateId;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DispenserMovementStatus status = DispenserMovementStatus.REGISTERED;

    @ElementCollection
    @CollectionTable(name = "dispenser_movement_serials", joinColumns = @JoinColumn(name = "movement_id"))
    @Column(name = "serial", length = 100)
    @Builder.Default
    private List<String> serials = new ArrayList<>();

    // Scanned serials flagged as "dispenser no registrado" in jMobile: kept for traceability but not sent out.
    @ElementCollection
    @CollectionTable(name = "dispenser_movement_excluded_serials", joinColumns = @JoinColumn(name = "movement_id"))
    @Column(name = "serial", length = 100)
    @Builder.Default
    private List<String> excludedSerials = new ArrayList<>();

    @Column(name = "aguas_movement_id", length = 50)
    private String aguasMovementId;

    // Odoo tracking, sent after Aguas succeeds: repair intake for UNLOAD, dispatch/salida to
    // reparto for LOAD. A row is always one type or the other, so these columns are shared
    // without conflict between the two flows.
    @Column(name = "odoo_status", length = 20)
    private String odooStatus;

    @Column(name = "odoo_picking_id")
    private Integer odooPickingId;

    @Column(name = "odoo_picking_name", length = 100)
    private String odooPickingName;

    // External reference sent to Odoo (LOAD/salida only) so retries reuse the same value and
    // Odoo can dedupe a retried call instead of moving stock twice.
    @Column(name = "odoo_reference", length = 100)
    private String odooReference;

    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "registered_by_username")
    private String registeredByUsername;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Serials actually forwarded to Aguas/Odoo: everything scanned minus the excluded ones. */
    public List<String> serialsToSend() {
        if (serials == null) return List.of();
        if (excludedSerials == null || excludedSerials.isEmpty()) return List.copyOf(serials);
        return serials.stream().filter(serial -> !excludedSerials.contains(serial)).toList();
    }
}
