package com.readdlytech.marketplaceapi;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import com.readdlytech.marketplaceapi.service.AiMarketplaceService;
import com.readdlytech.marketplaceapi.service.AiServiceClient;
import com.readdlytech.marketplaceapi.service.PartService;
import com.readdlytech.marketplaceapi.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMarketplaceServiceTests {
    @Mock AiServiceClient aiServiceClient;
    @Mock PartRepository partRepository;
    @Mock PartService partService;
    @Mock RecommendationService recommendationService;
    @InjectMocks AiMarketplaceService service;

    @Test
    void preservesRankingExplanationsAndExtractedConstraints() {
        AiSearchFilter filters = new AiSearchFilter(null, null, null, null, null, null,
                null, null, null, null);
        when(aiServiceClient.search("front brake part for a diesel Golf", 12, .5, filters))
                .thenReturn(new AiServiceSearchResponse("front brake part for a diesel Golf",
                        List.of(new AiSearchHit(7L, .91, .55,
                                List.of("Golf compatible", "Diesel", "Brake system"))), 14,
                        Map.of("model", "Golf", "fuelType", "Diesel", "category", "Brake Systems"),
                        new GroundedAnswerResponse("Listing #7 is the strongest match.", List.of(7L),
                                List.of("Confirm exact fitment."), "ollama")));
        Part part = new Part();
        part.setId(7L);
        part.setStatus(PartStatus.ACTIVE);
        when(partRepository.findAllById(List.of(7L))).thenReturn(List.of(part));
        PartResponse partResponse = new PartResponse();
        partResponse.setId(7L);
        partResponse.setTitle("Database-backed Golf brake caliper");
        when(partService.toResponse(part, null)).thenReturn(partResponse);

        SemanticSearchResponse response = service.search(
                "front brake part for a diesel Golf", 12, .5, filters, null);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().score()).isEqualTo(.91);
        assertThat(response.results().getFirst().matchReasons())
                .containsExactly("Golf compatible", "Diesel", "Brake system");
        assertThat(response.extractedConstraints()).containsEntry("model", "Golf");
        assertThat(response.answer().recommendedListingIds()).containsExactly(7L);
        assertThat(response.answer().summary())
                .contains("Listing #7")
                .contains("Database-backed Golf brake caliper")
                .doesNotContain("strongest match");
        assertThat(response.answer().generatedBy()).isEqualTo("deterministic");
    }

    @Test
    void constructsNoResultAnswerWithoutUsingAiGeneratedProse() {
        AiSearchFilter filters = new AiSearchFilter(null, null, null, null, null, null,
                null, null, null, null);
        when(aiServiceClient.search("impossible part", 12, .5, filters))
                .thenReturn(new AiServiceSearchResponse("impossible part", List.of(), 5, Map.of(),
                        new GroundedAnswerResponse("Invented recommendation", List.of(999L), List.of(), "ollama")));
        when(partRepository.findAllById(List.of())).thenReturn(List.of());

        SemanticSearchResponse response = service.search("impossible part", 12, .5, filters, null);

        assertThat(response.answer().summary()).contains("No active listing").doesNotContain("Invented");
        assertThat(response.answer().recommendedListingIds()).isEmpty();
        assertThat(response.answer().generatedBy()).isEqualTo("deterministic");
    }

    @Test
    void assistantUsesSanitizedRecommendationsAndRehydratesActiveListings() {
        User buyer = new User(); buyer.setId(3L); buyer.setRole(UserRole.BUYER);
        User seller = new User(); seller.setId(8L); seller.setSuspended(false);
        PartResponse recommendedPart = new PartResponse(); recommendedPart.setId(7L); recommendedPart.setTitle("Golf brake caliper");
        when(recommendationService.recommend(buyer, 12)).thenReturn(new RecommendationResponse("PERSONALIZED",
                List.of(new RecommendationResponse.Item(recommendedPart, 44.0,
                        List.of("Listed for your Volkswagen Golf"))), "Confirm fitment"));
        AiSearchFilter filters = new AiSearchFilter(null, null, null, null, null, null,
                null, null, null, null);
        when(aiServiceClient.assist("recommend a Golf brake for me", 6, .5, filters,
                List.of(new AiRecommendationCandidate(7L, 44.0, List.of("Listed for your Volkswagen Golf")))))
                .thenReturn(new AiServiceAssistantResponse("untrusted query", "RECOMMENDATION",
                        List.of(new AiAssistantResult(7L, 1.0, List.of("Listed for your Volkswagen Golf"), "RECOMMENDATION")),
                        18, Map.of("model", "Golf"), List.of("search_agent", "recommendation_agent", "unknown_tool"),
                        new GroundedAnswerResponse("Untrusted generated prose", List.of(999L), List.of(), "ollama")));
        Part part = new Part(); part.setId(7L); part.setStatus(PartStatus.ACTIVE); part.setSeller(seller);
        when(partRepository.findAllById(List.of(7L))).thenReturn(List.of(part));
        when(partService.toResponse(part, 3L)).thenReturn(recommendedPart);

        MarketplaceAssistantResponse response = service.assist("recommend a Golf brake for me", 6, .5, filters, buyer);

        assertThat(response.query()).isEqualTo("recommend a Golf brake for me");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().part().getId()).isEqualTo(7L);
        assertThat(response.toolsUsed()).containsExactly("search_agent", "recommendation_agent");
        assertThat(response.answer().summary()).contains("Listing #7").doesNotContain("Untrusted");
        assertThat(response.answer().recommendedListingIds()).containsExactly(7L);
        assertThat(response.answer().generatedBy()).isEqualTo("deterministic");
    }
}
