package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.VehicleResponse;
import com.readdlytech.marketplaceapi.dto.VehicleCatalogMakeResponse;
import com.readdlytech.marketplaceapi.repository.VehicleRepository;
import com.readdlytech.marketplaceapi.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleRepository vehicleRepository;
    private final VehicleCatalogService vehicleCatalogService;

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> vehicles(@RequestParam(required = false) String brand) {
        var vehicles = brand == null || brand.isBlank()
                ? vehicleRepository.findAll()
                : vehicleRepository.findByBrand(brand);
        return ResponseEntity.ok(vehicles.stream().map(VehicleResponse::from).toList());
    }

    @GetMapping("/catalog/years")
    public ResponseEntity<List<Integer>> catalogYears() {
        return ResponseEntity.ok(vehicleCatalogService.years());
    }

    @GetMapping("/catalog/makes")
    public ResponseEntity<List<VehicleCatalogMakeResponse>> catalogMakes(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(vehicleCatalogService.makes(year));
    }

    @GetMapping("/catalog/models")
    public ResponseEntity<List<String>> catalogModels(@RequestParam String makeId,
                                                       @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(vehicleCatalogService.models(makeId, year));
    }
}
