package com.readdlytech.marketplaceapi.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class PartRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be 255 characters or fewer")
    private String title;

    @Size(max = 5000, message = "Description must be 5000 characters or fewer")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @Size(max = 50, message = "Condition must be 50 characters or fewer")
    private String condition;

    private Long categoryId;

    private Long vehicleId;

    @Size(max = 20, message = "A listing can have at most 20 compatible vehicles")
    private Set<Long> vehicleIds = new LinkedHashSet<>();

    @Size(max = 120, message = "Manufacturer must be 120 characters or fewer")
    private String manufacturer;

    @Size(max = 120, message = "Part brand must be 120 characters or fewer")
    private String partBrand;

    @Size(max = 100, message = "OEM number must be 100 characters or fewer")
    private String oemNumber;

    @Size(max = 20, message = "A listing can have at most 20 alternative OEM numbers")
    private Set<@Size(max = 100, message = "Alternative OEM numbers must be 100 characters or fewer") String> alternativeOemNumbers = new LinkedHashSet<>();

    @Size(max = 30, message = "Part position must be 30 characters or fewer")
    private String partPosition;

    @Size(max = 255, message = "Engine compatibility must be 255 characters or fewer")
    private String engineCompatibility;

    @Size(max = 100, message = "Fuel compatibility must be 100 characters or fewer")
    private String fuelCompatibility;

    @Size(max = 500, message = "Warranty information must be 500 characters or fewer")
    private String warrantyInformation;

    private boolean deliveryAvailable;
    private boolean collectionAvailable = true;
    private boolean negotiable;

    @Size(max = 255, message = "Location must be 255 characters or fewer")
    private String location;
}
