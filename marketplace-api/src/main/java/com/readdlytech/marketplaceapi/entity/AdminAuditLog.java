package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs", indexes = @Index(name = "idx_admin_audit_created", columnList = "created_at"))
@Data
@NoArgsConstructor
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "admin_id", nullable = false)
    private Long adminId;
    @Column(name = "admin_email", nullable = false, length = 100)
    private String adminEmail;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;
    @Column(name = "target_id")
    private Long targetId;
    @Column(name = "target_label", length = 255)
    private String targetLabel;
    @Column(length = 1000)
    private String details;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
