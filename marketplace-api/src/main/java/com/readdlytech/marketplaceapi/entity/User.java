package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String location;

    @Column(length = 160)
    private String businessName;

    @Column(length = 255)
    private String website;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role = UserRole.BUYER;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_application_status", length = 20)
    private SellerApplicationStatus sellerApplicationStatus = SellerApplicationStatus.NONE;

    @Column(name = "seller_application_submitted_at")
    private LocalDateTime sellerApplicationSubmittedAt;

    @Column(name = "seller_application_reviewed_at")
    private LocalDateTime sellerApplicationReviewedAt;

    @Column(name = "seller_application_reviewed_by_id")
    private Long sellerApplicationReviewedById;

    @Column(name = "seller_application_review_note", length = 500)
    private String sellerApplicationReviewNote;

    @Column(nullable = false)
    private Boolean suspended = false;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_by_id")
    private Long suspendedById;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Part> parts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorite> favorites = new ArrayList<>();
}

