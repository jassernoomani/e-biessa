package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.VinDecodeResponse;
import com.readdlytech.marketplaceapi.entity.Vehicle;
import com.readdlytech.marketplaceapi.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class VinDecoderService {
    private static final String VIN_PATTERN = "[A-HJ-NPR-Z0-9]{17}";
    private final VehicleRepository vehicles;
    private final RestClient client;
    private final boolean enabled;
    private final long cacheHours;
    private final Map<String, CachedDecode> cache = new ConcurrentHashMap<>();

    public VinDecoderService(VehicleRepository vehicles, RestClient.Builder builder,
                             @Value("${app.vin-decoder.enabled:true}") boolean enabled,
                             @Value("${app.vin-decoder.base-url:https://vpic.nhtsa.dot.gov/api}") String baseUrl,
                             @Value("${app.vin-decoder.timeout-seconds:8}") int timeoutSeconds,
                             @Value("${app.vin-decoder.cache-hours:24}") long cacheHours) {
        this.vehicles = vehicles;
        this.enabled = enabled;
        this.cacheHours = Math.max(1, cacheHours);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public VinDecodeResponse decode(String rawVin) {
        String vin = normalizeVin(rawVin);
        if (!enabled) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "VIN decoding is disabled.");
        CachedDecode cached = cache.get(vin);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.response();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.get()
                    .uri("/vehicles/DecodeVinValues/{vin}?format=json", vin)
                    .retrieve().body(Map.class);
            VinDecodeResponse response = mapResponse(vin, body);
            cache.put(vin, new CachedDecode(response, Instant.now().plus(Duration.ofHours(cacheHours))));
            if (cache.size() > 1_000) cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    "The VIN decoder is temporarily unavailable. Enter your vehicle manually.");
        }
    }

    static String normalizeVin(String value) {
        String vin = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!vin.matches(VIN_PATTERN))
            throw new IllegalArgumentException("Enter a valid 17-character VIN without I, O, or Q.");
        return vin;
    }

    VinDecodeResponse mapResponse(String vin, Map<String, Object> body) {
        Map<String, Object> decoded = firstResult(body);
        String make = text(decoded.get("Make"));
        String model = text(decoded.get("Model"));
        Integer year = integer(decoded.get("ModelYear"));
        String fuel = canonicalFuel(text(decoded.get("FuelTypePrimary")));
        String engine = engine(decoded);
        if (make == null || model == null)
            throw new IllegalArgumentException("This VIN could not be decoded to a make and model.");
        List<Long> matches = vehicles.findAll().stream()
                .filter(vehicle -> equal(vehicle.getBrand(), make) && equal(vehicle.getModel(), model))
                .filter(vehicle -> year == null || vehicle.getYear() == null || year.equals(vehicle.getYear()))
                .filter(vehicle -> fuel == null || vehicle.getFuelType() == null || fuelCompatible(vehicle.getFuelType(), fuel))
                .map(Vehicle::getId).filter(Objects::nonNull).sorted().toList();
        List<String> warnings = new ArrayList<>();
        warnings.add("VIN data identifies the vehicle; it does not guarantee that a specific used part fits.");
        if (matches.isEmpty()) warnings.add("No exact vehicle configuration exists in the local catalog; choose it manually or ask the seller.");
        return new VinDecodeResponse(vin, make, model, year, engine, fuel, matches, warnings);
    }

    private static Map<String, Object> firstResult(Map<String, Object> body) {
        if (body == null || !(body.get("Results") instanceof List<?> results) || results.isEmpty()
                || !(results.getFirst() instanceof Map<?, ?> first))
            throw new ResponseStatusException(BAD_GATEWAY, "The VIN decoder returned an invalid response.");
        Map<String, Object> decoded = new HashMap<>();
        first.forEach((key, value) -> decoded.put(String.valueOf(key), value));
        return decoded;
    }

    private static String engine(Map<String, Object> decoded) {
        String model = text(decoded.get("EngineModel"));
        if (model != null) return model;
        String displacement = text(decoded.get("DisplacementL"));
        String cylinders = text(decoded.get("EngineCylinders"));
        String value = String.join(" · ", displacement == null ? "" : displacement + "L",
                cylinders == null ? "" : cylinders + " cylinders").replaceAll("^ · | · $", "");
        return value.isBlank() ? null : value;
    }

    private static String canonicalFuel(String value) {
        if (value == null) return null;
        String normalized = norm(value);
        if (normalized.contains("diesel")) return "Diesel";
        if (normalized.contains("electric")) return "Electric";
        if (normalized.contains("hybrid")) return "Hybrid";
        if (normalized.contains("gasoline") || normalized.contains("petrol")) return "Petrol";
        return value;
    }

    private static boolean fuelCompatible(String local, String decoded) {
        return canonicalFuel(local).equalsIgnoreCase(canonicalFuel(decoded));
    }
    private static boolean equal(String left, String right) { return norm(left).equals(norm(right)); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String text(Object value) { String text = value == null ? "" : String.valueOf(value).trim(); return text.isBlank() ? null : text; }
    private static Integer integer(Object value) { try { String text = text(value); return text == null ? null : Integer.valueOf(text); } catch (NumberFormatException ignored) { return null; } }
    private record CachedDecode(VinDecodeResponse response, Instant expiresAt) {}
}
