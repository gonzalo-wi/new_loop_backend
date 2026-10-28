package com.loop.new_loop_api.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Raw quantities of one product in a control at the time of the event; the difference rule is applied when querying. */
@Entity
@Immutable
@Table(name = "operation_event_items")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationEventItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private OperationEvent event;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer fullQuantity;

    @Column(nullable = false)
    private Integer exchangeQuantity;
}
