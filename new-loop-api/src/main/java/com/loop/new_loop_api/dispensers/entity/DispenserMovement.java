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
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DispenserMovementStatus status = DispenserMovementStatus.REGISTERED;

    @ElementCollection
    @CollectionTable(name = "dispenser_movement_serials", joinColumns = @JoinColumn(name = "movement_id"))
    @Column(name = "serial", length = 100)
    @Builder.Default
    private List<String> serials = new ArrayList<>();

    @Column(name = "aguas_movement_id", length = 50)
    private String aguasMovementId;

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
}
