package com.readdlytech.marketplaceapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartResponse {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String condition;
    private String categoryName;
    private Long categoryId;
    private String sellerName;
    private Long sellerId;
    private String brand;
    private String model;
    private Integer year;
    private List<VehicleResponse> compatibleVehicles;
    private String manufacturer;
    private String partBrand;
    private String oemNumber;
    private List<String> alternativeOemNumbers;
    private String partPosition;
    private String engineCompatibility;
    private String fuelCompatibility;
    private String warrantyInformation;
    private boolean deliveryAvailable;
    private boolean collectionAvailable;
    private boolean negotiable;
    private String location;
    private String status;
    private Integer views;
    private LocalDateTime createdAt;
    private LocalDateTime soldAt;
    private List<String> imageUrls;
    private List<ImageResponse> images;
    private Long favoriteCount;
    private boolean isFavorited;
}
