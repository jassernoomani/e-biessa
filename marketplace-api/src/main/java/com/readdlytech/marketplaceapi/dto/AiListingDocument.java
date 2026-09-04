package com.readdlytech.marketplaceapi.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiListingDocument(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String condition,
        String categoryName,
        Long sellerId,
        String sellerName,
        List<VehicleResponse> compatibleVehicles,
        String manufacturer,
        String partBrand,
        String oemNumber,
        List<String> alternativeOemNumbers,
        String partPosition,
        String engineCompatibility,
        String fuelCompatibility,
        String warrantyInformation,
        boolean deliveryAvailable,
        boolean collectionAvailable,
        boolean negotiable,
        String location,
        String status
) {
    public static AiListingDocument from(PartResponse part) {
        return new AiListingDocument(
                part.getId(), part.getTitle(), part.getDescription(), part.getPrice(), part.getCondition(),
                part.getCategoryName(), part.getSellerId(), part.getSellerName(), part.getCompatibleVehicles(),
                part.getManufacturer(), part.getPartBrand(), part.getOemNumber(), part.getAlternativeOemNumbers(),
                part.getPartPosition(), part.getEngineCompatibility(), part.getFuelCompatibility(),
                part.getWarrantyInformation(), part.isDeliveryAvailable(), part.isCollectionAvailable(),
                part.isNegotiable(), part.getLocation(), part.getStatus()
        );
    }
}

