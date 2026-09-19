package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record VinDecodeResponse(String vin, String make, String model, Integer year,
                                String engine, String fuelType, List<Long> matchedVehicleIds,
                                List<String> warnings) {}
