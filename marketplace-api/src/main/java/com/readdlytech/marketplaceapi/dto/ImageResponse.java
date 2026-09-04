package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.Image;

public record ImageResponse(Long id, String url, Boolean primary, Integer sortOrder) {
    public static ImageResponse from(Image image) {
        return new ImageResponse(image.getId(), image.getUrl(), image.getIsPrimary(), image.getSortOrder());
    }
}
