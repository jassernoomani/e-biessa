package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"part_id", "buyer_id"}))
@Data
@NoArgsConstructor
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "part_id", nullable = false)
    private Part part;
    @ManyToOne(optional = false) @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;
    @ManyToOne(optional = false) @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    @CreationTimestamp @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();
}
