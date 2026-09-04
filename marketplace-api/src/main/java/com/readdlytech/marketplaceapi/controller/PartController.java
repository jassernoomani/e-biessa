package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.PartRequest;
import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.PartStatusRequest;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.PartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
public class PartController {
    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "price", "views", "title");
    private final PartService partService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<Page<PartResponse>> getParts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer yearMin,
            @RequestParam(required = false) Integer yearMax,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String partBrand,
            @RequestParam(required = false) String partPosition,
            @RequestParam(required = false) String engine,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String warranty,
            @RequestParam(required = false) Boolean deliveryAvailable,
            @RequestParam(required = false) Boolean collectionAvailable,
            @RequestParam(required = false) Boolean negotiable,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORTS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported listing sort: " + order.getProperty());
            }
        });

        Long userId = currentUserService.optional(authentication).map(User::getId).orElse(null);
        Specification<Part> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                var alternativeOems = root.<Part, String>joinSet("alternativeOemNumbers", JoinType.LEFT);
                return cb.or(cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("oemNumber")), pattern),
                        cb.like(cb.lower(alternativeOems), pattern));
            });
        }
        if (categoryId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        if (hasText(brand) || hasText(model) || year != null || yearMin != null || yearMax != null || hasText(engine) || hasText(fuelType)) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                var compatible = root.join("compatibleVehicles", JoinType.LEFT);
                var legacy = root.join("vehicle", JoinType.LEFT);
                Predicate compatibleMatch = cb.conjunction();
                Predicate legacyMatch = cb.conjunction();
                if (hasText(brand)) { compatibleMatch = cb.and(compatibleMatch, cb.equal(cb.lower(compatible.get("brand")), brand.toLowerCase())); legacyMatch = cb.and(legacyMatch, cb.equal(cb.lower(legacy.get("brand")), brand.toLowerCase())); }
                if (hasText(model)) { compatibleMatch = cb.and(compatibleMatch, cb.equal(cb.lower(compatible.get("model")), model.toLowerCase())); legacyMatch = cb.and(legacyMatch, cb.equal(cb.lower(legacy.get("model")), model.toLowerCase())); }
                if (year != null) { compatibleMatch = cb.and(compatibleMatch, cb.equal(compatible.get("year"), year)); legacyMatch = cb.and(legacyMatch, cb.equal(legacy.get("year"), year)); }
                if (yearMin != null) { compatibleMatch = cb.and(compatibleMatch, cb.greaterThanOrEqualTo(compatible.get("year"), yearMin)); legacyMatch = cb.and(legacyMatch, cb.greaterThanOrEqualTo(legacy.get("year"), yearMin)); }
                if (yearMax != null) { compatibleMatch = cb.and(compatibleMatch, cb.lessThanOrEqualTo(compatible.get("year"), yearMax)); legacyMatch = cb.and(legacyMatch, cb.lessThanOrEqualTo(legacy.get("year"), yearMax)); }
                if (hasText(engine)) { compatibleMatch = cb.and(compatibleMatch, cb.like(cb.lower(compatible.get("engine")), "%" + engine.toLowerCase() + "%")); legacyMatch = cb.and(legacyMatch, cb.like(cb.lower(legacy.get("engine")), "%" + engine.toLowerCase() + "%")); }
                if (hasText(fuelType)) { compatibleMatch = cb.and(compatibleMatch, cb.equal(cb.lower(compatible.get("fuelType")), fuelType.toLowerCase())); legacyMatch = cb.and(legacyMatch, cb.equal(cb.lower(legacy.get("fuelType")), fuelType.toLowerCase())); }
                return cb.or(compatibleMatch, legacyMatch);
            });
        }
        if (minPrice != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        if (maxPrice != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        if (condition != null && !condition.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("condition")), condition.toLowerCase()));
        if (hasText(manufacturer)) spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("manufacturer")), manufacturer.toLowerCase()));
        if (hasText(partBrand)) spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("partBrand")), partBrand.toLowerCase()));
        if (hasText(partPosition)) spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("partPosition")), partPosition.toLowerCase()));
        if (hasText(warranty)) spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("warrantyInformation")), "%" + warranty.toLowerCase() + "%"));
        if (deliveryAvailable != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("deliveryAvailable"), deliveryAvailable));
        if (collectionAvailable != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("collectionAvailable"), collectionAvailable));
        if (negotiable != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("negotiable"), negotiable));
        spec = spec.and(partService.publiclyVisibleSpecification());
        return ResponseEntity.ok(partService.getAllParts(spec, pageable, userId));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @GetMapping("/my-listings")
    public ResponseEntity<List<PartResponse>> myListings(Authentication authentication) {
        return ResponseEntity.ok(partService.getMyListings(currentUserService.require(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartResponse> getPart(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.optional(authentication).map(User::getId).orElse(null);
        return ResponseEntity.ok(partService.getPartById(id, userId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(partService.createPart(request, currentUserService.require(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<PartResponse> update(@PathVariable Long id, @Valid @RequestBody PartRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok(partService.updatePart(id, request, currentUserService.require(authentication)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<PartResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody PartStatusRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.ok(partService.updateStatus(id, request.status(), currentUserService.require(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        partService.deletePart(id, currentUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }
}
