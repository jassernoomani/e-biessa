package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "parts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "part_condition", length = 50)  // Not a reserved word
    private String condition;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToMany
    @JoinTable(name = "part_compatible_vehicles",
            joinColumns = @JoinColumn(name = "part_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    @OrderBy("brand ASC, model ASC, year ASC")
    private Set<Vehicle> compatibleVehicles = new LinkedHashSet<>();

    @Column(length = 120)
    private String manufacturer;

    @Column(name = "part_brand", length = 120)
    private String partBrand;

    @Column(name = "oem_number", length = 100)
    private String oemNumber;

    @ElementCollection
    @CollectionTable(name = "part_alternative_oem_numbers", joinColumns = @JoinColumn(name = "part_id"))
    @Column(name = "oem_number", length = 100)
    private Set<String> alternativeOemNumbers = new LinkedHashSet<>();

    @Column(name = "part_position", length = 30)
    private String partPosition;

    @Column(name = "engine_compatibility", length = 255)
    private String engineCompatibility;

    @Column(name = "fuel_compatibility", length = 100)
    private String fuelCompatibility;

    @Column(name = "warranty_information", length = 500)
    private String warrantyInformation;

    @Column(name = "delivery_available", nullable = false)
    private Boolean deliveryAvailable = false;

    @Column(name = "collection_available", nullable = false)
    private Boolean collectionAvailable = true;

    @Column(nullable = false)
    private Boolean negotiable = false;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PartStatus status = PartStatus.ACTIVE;

    private Integer views = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Image> images = new ArrayList<>();

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListingReport> reports = new ArrayList<>();

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Conversation> conversations = new ArrayList<>();
}

