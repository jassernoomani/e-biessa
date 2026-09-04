package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.Category;
import java.util.List;

public record CategoryResponse(Long id, String name, List<CategoryResponse> subCategories) {
    public static CategoryResponse from(Category category) {
        List<CategoryResponse> children = category.getSubCategories() == null ? List.of() :
                category.getSubCategories().stream().map(CategoryResponse::from).toList();
        return new CategoryResponse(category.getId(), category.getName(), children);
    }
}
