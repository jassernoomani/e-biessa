package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Bounded, deduplicated signals only: never queries, contact details or message bodies. */
@Entity
@Getter @Setter
@Table(name = "buyer_interactions", uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "kind", "target_id"}),
        indexes = @Index(name = "idx_buyer_activity", columnList = "buyer_id,occurred_at"))
public class BuyerInteraction {
    public enum Kind { VIEW, SEARCH, CONTACT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Kind kind;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    // Snapshot identifiers deliberately have no foreign keys to deletable listings/categories.
    private Long categoryId;
    private Long sellerId;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
