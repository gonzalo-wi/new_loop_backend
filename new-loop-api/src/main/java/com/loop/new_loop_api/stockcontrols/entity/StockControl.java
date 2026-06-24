package com.loop.new_loop_api.stockcontrols.entity;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.routes.entity.Route;
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
@Table(name = "stock_controls")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockControl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ControlType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ControlStatus status = ControlStatus.CONTROLLED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "controller_id")
    private UUID controllerId;

    @Column(nullable = false)
    private LocalDate controlDate;

    @Column(length = 500)
    private String observations;

    @OneToMany(mappedBy = "stockControl", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockControlItem> items = new ArrayList<>();

    private LocalDateTime confirmedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
