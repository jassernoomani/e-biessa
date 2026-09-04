package com.readdlytech.marketplaceapi.dto;

public record SellerContactResponse(
        Long sellerId,
        String sellerName,
        String businessName,
        String phone,
        String email
) {}
