package com.readdlytech.marketplaceapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readdlytech.marketplaceapi.dto.VehicleCatalogMakeResponse;
import com.readdlytech.marketplaceapi.entity.Vehicle;
import com.readdlytech.marketplaceapi.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Year;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class VehicleCatalogService {
    private final VehicleRepository vehicleRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final AtomicLong unavailableUntil = new AtomicLong(0);

    public VehicleCatalogService(
            VehicleRepository vehicleRepository,
            ObjectMapper objectMapper,
            @Value("${app.car-query.enabled:true}") boolean enabled,
            @Value("${app.car-query.base-url:https://www.carqueryapi.com/api/0.3/}") String baseUrl,
            @Value("${app.car-query.timeout-seconds:5}") long timeoutSeconds) {
        this.vehicleRepository = vehicleRepository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<Integer> years() {
        if (canCallRemote()) {
            try {
                JsonNode years = call("getYears", Map.of()).path("Years");
                int min = years.path("min_year").asInt();
                int max = years.path("max_year").asInt();
                if (min > 1800 && max >= min) {
                    return IntStream.iterate(max, year -> year >= min, year -> year - 1).boxed().toList();
                }
            } catch (Exception exception) {
                logUnavailable(exception);
            }
        }
        int max = Year.now().getValue() + 1;
        return IntStream.iterate(max, year -> year >= 1941, year -> year - 1).boxed().toList();
    }

    public List<VehicleCatalogMakeResponse> makes(Integer year) {
        if (canCallRemote()) {
            try {
                Map<String, String> parameters = new LinkedHashMap<>();
                if (year != null) parameters.put("year", year.toString());
                JsonNode makes = call("getMakes", parameters).path("Makes");
                Map<String, VehicleCatalogMakeResponse> unique = new LinkedHashMap<>();
                for (JsonNode make : makes) {
                    if (!"1".equals(make.path("make_is_common").asText())) continue;
                    String id = clean(make.path("make_id").asText());
                    String name = clean(make.path("make_display").asText());
                    if (!id.isBlank() && !name.isBlank()) {
                        unique.putIfAbsent(id.toLowerCase(Locale.ROOT),
                                new VehicleCatalogMakeResponse(id, name, clean(make.path("make_country").asText())));
                    }
                }
                if (!unique.isEmpty()) return unique.values().stream().sorted(makeComparator()).toList();
            } catch (Exception exception) {
                logUnavailable(exception);
            }
        }
        return fallbackMakes(year);
    }

    public List<String> models(String makeId, Integer year) {
        if (makeId == null || makeId.isBlank()) return List.of();
        if (canCallRemote()) {
            try {
                Map<String, String> parameters = new LinkedHashMap<>();
                parameters.put("make", makeId.trim());
                if (year != null) parameters.put("year", year.toString());
                JsonNode models = call("getModels", parameters).path("Models");
                Map<String, String> unique = new LinkedHashMap<>();
                for (JsonNode model : models) {
                    String name = clean(model.path("model_name").asText());
                    if (!name.isBlank()) unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                }
                if (!unique.isEmpty()) return unique.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            } catch (Exception exception) {
                logUnavailable(exception);
            }
        }
        return fallbackModels(makeId, year);
    }

    private JsonNode call(String command, Map<String, String> parameters) throws Exception {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("cmd", command);
        parameters.forEach(uriBuilder::queryParam);
        URI uri = uriBuilder.build().encode().toUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json, application/javascript")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("CarQuery returned HTTP " + response.statusCode());
        }
        String body = response.body().trim();
        int jsonStart = body.indexOf('{');
        int jsonEnd = body.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd < jsonStart) throw new IllegalStateException("CarQuery returned invalid JSON");
        return objectMapper.readTree(body.substring(jsonStart, jsonEnd + 1));
    }

    private List<VehicleCatalogMakeResponse> fallbackMakes(Integer year) {
        Map<String, VehicleCatalogMakeResponse> unique = new LinkedHashMap<>();
        vehicleRepository.findAll().stream()
                .filter(vehicle -> year == null || year.equals(vehicle.getYear()))
                .forEach(vehicle -> {
                    String name = clean(vehicle.getBrand());
                    if (!name.isBlank()) unique.putIfAbsent(name.toLowerCase(Locale.ROOT),
                            new VehicleCatalogMakeResponse(slug(name), name, ""));
                });
        return unique.values().stream().sorted(makeComparator()).toList();
    }

    private List<String> fallbackModels(String makeId, Integer year) {
        String normalizedMake = slug(makeId);
        return vehicleRepository.findAll().stream()
                .filter(vehicle -> slug(vehicle.getBrand()).equals(normalizedMake))
                .filter(vehicle -> year == null || year.equals(vehicle.getYear()))
                .map(Vehicle::getModel)
                .map(this::clean)
                .filter(model -> !model.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Comparator<VehicleCatalogMakeResponse> makeComparator() {
        return Comparator.comparing(VehicleCatalogMakeResponse::name, String.CASE_INSENSITIVE_ORDER);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String slug(String value) {
        String ascii = Normalizer.normalize(clean(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private void logUnavailable(Exception exception) {
        unavailableUntil.set(System.currentTimeMillis() + Duration.ofMinutes(5).toMillis());
        log.warn("CarQuery catalog is unavailable; using listing-derived fallback data: {}", exception.getMessage());
    }

    private boolean canCallRemote() {
        return enabled && System.currentTimeMillis() >= unavailableUntil.get();
    }
}
