package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.CategoryResponse;
import com.readdlytech.marketplaceapi.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryRepository categoryRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findByParentIsNull().stream()
                .map(CategoryResponse::from).toList());
    }
}
