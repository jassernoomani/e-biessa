package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.Vehicle;

public record VehicleResponse(Long id, String brand, String model, Integer year, String engine, String fuelType) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(), vehicle.getYear(),
                vehicle.getEngine(), vehicle.getFuelType());
    }
}
