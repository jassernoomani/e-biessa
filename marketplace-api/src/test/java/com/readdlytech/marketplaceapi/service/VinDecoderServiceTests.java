package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.VinDecodeResponse;
import com.readdlytech.marketplaceapi.entity.Vehicle;
import com.readdlytech.marketplaceapi.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VinDecoderServiceTests {
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final VinDecoderService service = new VinDecoderService(
            vehicles, RestClient.builder(), false, "https://example.test", 1, 24);

    @Test
    void normalizesAndValidatesFullVins() {
        assertThat(VinDecoderService.normalizeVin("  wvwzzz1kzaw000001 "))
                .isEqualTo("WVWZZZ1KZAW000001");
        assertThatThrownBy(() -> VinDecoderService.normalizeVin("not-a-vin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("17-character VIN");
        assertThatThrownBy(() -> VinDecoderService.normalizeVin("WVWZZZ1KZAI000001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsDecodedVehicleToAnExactLocalCatalogConfiguration() {
        Vehicle golf = new Vehicle();
        golf.setId(11L);
        golf.setBrand("Volkswagen");
        golf.setModel("Golf");
        golf.setYear(2020);
        golf.setEngine("2.0 TDI");
        golf.setFuelType("Diesel");
        when(vehicles.findAll()).thenReturn(List.of(golf));

        Map<String, Object> decoded = Map.of("Results", List.of(Map.of(
                "Make", "VOLKSWAGEN",
                "Model", "Golf",
                "ModelYear", "2020",
                "FuelTypePrimary", "Diesel",
                "DisplacementL", "2.0",
                "EngineCylinders", "4"
        )));

        VinDecodeResponse response = service.mapResponse("WVWZZZ1KZAW000001", decoded);

        assertThat(response.make()).isEqualTo("VOLKSWAGEN");
        assertThat(response.model()).isEqualTo("Golf");
        assertThat(response.year()).isEqualTo(2020);
        assertThat(response.fuelType()).isEqualTo("Diesel");
        assertThat(response.engine()).isEqualTo("2.0L · 4 cylinders");
        assertThat(response.matchedVehicleIds()).containsExactly(11L);
        assertThat(response.warnings()).hasSize(1);
    }

    @Test
    void warnsWhenTheDecodedConfigurationIsMissingFromTheCatalog() {
        when(vehicles.findAll()).thenReturn(List.of());
        Map<String, Object> decoded = Map.of("Results", List.of(Map.of(
                "Make", "RENAULT", "Model", "Clio", "ModelYear", "2018"
        )));

        VinDecodeResponse response = service.mapResponse("VF1AAAAAA00000001", decoded);

        assertThat(response.matchedVehicleIds()).isEmpty();
        assertThat(response.warnings()).anyMatch(message -> message.contains("local catalog"));
    }
}
