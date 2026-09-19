package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PriceEstimateRequest;
import com.readdlytech.marketplaceapi.dto.PriceEstimateResponse;
import com.readdlytech.marketplaceapi.entity.Category;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.Vehicle;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceEstimatorServiceTests {
    private final PartRepository parts = mock(PartRepository.class);
    private final PriceEstimatorService service = new PriceEstimatorService(parts);

    @Test
    void calculatesAReproducibleMedianAndRangeFromSimilarActiveListings() {
        Category brakes = new Category();
        brakes.setId(2L);
        Vehicle golf = new Vehicle();
        golf.setId(7L);
        when(parts.findByStatus(PartStatus.ACTIVE)).thenReturn(List.of(
                part(1, "Golf brake caliper", "250", brakes, golf),
                part(2, "Front brake caliper", "300", brakes, golf),
                part(3, "Brake caliper carrier", "400", brakes, golf),
                part(4, "Golf headlight", "700", null, null)
        ));

        PriceEstimateResponse result = service.estimate(new PriceEstimateRequest(
                null, "Golf front brake caliper", 2L, Set.of(7L), "Used - good",
                null, null, "FRONT"));

        assertThat(result.comparableCount()).isEqualTo(3);
        assertThat(result.suggestedPrice()).isEqualByComparingTo("300.00");
        assertThat(result.lowerRange()).isEqualByComparingTo("300.00");
        assertThat(result.upperRange()).isEqualByComparingTo("400.00");
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.comparableListingIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("vehicle compatibility"));
    }

    @Test
    void returnsNoEstimateWhenNoListingIsSufficientlySimilar() {
        when(parts.findByStatus(PartStatus.ACTIVE)).thenReturn(List.of(
                part(4, "Renault Clio mirror", "150", null, null)));

        PriceEstimateResponse result = service.estimate(new PriceEstimateRequest(
                null, "Volkswagen Golf alternator", null, Set.of(), null,
                null, null, null));

        assertThat(result.comparableCount()).isZero();
        assertThat(result.suggestedPrice()).isNull();
        assertThat(result.confidence()).isEqualTo("INSUFFICIENT");
    }

    @Test
    void requiresAtLeastOneComparableIdentitySignal() {
        assertThatThrownBy(() -> service.estimate(new PriceEstimateRequest(
                null, " ", null, Set.of(), null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before estimating");
    }

    private Part part(long id, String title, String price, Category category, Vehicle vehicle) {
        Part part = new Part();
        part.setId(id);
        part.setTitle(title);
        part.setPrice(new BigDecimal(price));
        part.setStatus(PartStatus.ACTIVE);
        part.setCondition("Used - good");
        part.setPartPosition("FRONT");
        part.setCategory(category);
        part.setCompatibleVehicles(vehicle == null ? new LinkedHashSet<>() : new LinkedHashSet<>(Set.of(vehicle)));
        return part;
    }
}
