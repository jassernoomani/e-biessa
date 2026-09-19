package com.readdlytech.marketplaceapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.repository.*;
import com.readdlytech.marketplaceapi.service.*;
import com.readdlytech.marketplaceapi.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RecommendationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PartRepository parts;
    @Autowired VehicleRepository vehicles;
    @Autowired CategoryRepository categories;
    @Autowired FavoriteRepository favorites;
    @Autowired ConversationRepository conversations;
    @Autowired BuyerInteractionRepository activities;
    @Autowired RecommendationService recommendations;
    @Autowired BuyerActivityService activity;
    @Autowired ObjectMapper json;
    User buyer, seller, other;
    Vehicle golf, clio;
    Category brakes, lighting;

    @BeforeEach void fixture() {
        buyer = account("recommend-buyer@test.local", UserRole.BUYER);
        other = account("recommend-other@test.local", UserRole.BUYER);
        seller = account("recommend-seller@test.local", UserRole.SELLER);
        golf = vehicles.save(new Vehicle(null, "Volkswagen", "Golf", 2020, "1.6 TDI", "Diesel"));
        clio = vehicles.save(new Vehicle(null, "Renault", "Clio", 2020, "1.2", "Petrol"));
        brakes = new Category(); brakes.setName("Recommendation brakes"); categories.save(brakes);
        lighting = new Category(); lighting.setName("Recommendation lighting"); categories.save(lighting);
    }

    @Test void accessAndValidationAreBuyerScoped() throws Exception {
        mvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recommendations").with(user(seller.getEmail()).roles("SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recommendations").with(user(seller.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recommendations").param("limit", "25").with(user(buyer.getEmail()).roles("BUYER")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/recommendations/settings").with(user(buyer.getEmail()).roles("BUYER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"personalizationEnabled\":true,\"vehicleIds\":[-1]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/recommendations/settings").with(user(buyer.getEmail()).roles("BUYER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"personalizationEnabled\":true,\"vehicleIds\":[999999]}"))
                .andExpect(status().isBadRequest());
        assertThat(recommendations.settings(other).vehicles()).isEmpty();
    }

    @Test void coldStartReturnsOnlyActiveNonSuspendedListingsAndDeterministicReasons() {
        Part visible = listing("Recent part", golf, brakes);
        Part sold = listing("Sold", golf, brakes); sold.setStatus(PartStatus.SOLD);
        Part inactive = listing("Inactive", golf, brakes); inactive.setStatus(PartStatus.INACTIVE);
        User blocked = account("recommend-blocked@test.local", UserRole.SELLER); blocked.setSuspended(true);
        Part hidden = listing("Hidden", golf, brakes); hidden.setSeller(blocked);
        RecommendationResponse result = recommendations.recommend(buyer, 24);
        assertThat(result.mode()).isEqualTo("DISCOVER");
        assertThat(result.results()).extracting(i -> i.part().getId()).contains(visible.getId()).doesNotContain(sold.getId(), inactive.getId(), hidden.getId());
        assertThat(result.results().stream().filter(i -> i.part().getId().equals(visible.getId())).findFirst().orElseThrow().reasons())
                .contains("Recent listing to explore");
        assertThat(recommendations.recommend(buyer, 24)).isEqualTo(result);
    }

    @Test void preferredVehicleRequiresSameConfigurationAndNoConflictingFuel() throws Exception {
        Part matching = listing("Golf caliper", golf, brakes);
        Part incompatible = listing("Clio caliper", clio, brakes);
        Part wrongFuel = listing("Contradictory diesel vehicle / petrol part", golf, brakes); wrongFuel.setFuelCompatibility("Petrol");
        Part multiple = listing("Multiple fitment", clio, brakes); multiple.getCompatibleVehicles().add(golf);
        mvc.perform(put("/api/recommendations/settings").with(user(buyer.getEmail()).roles("BUYER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RecommendationSettingsRequest(true, List.of(golf.getId())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicles[0].model").value("Golf"));
        RecommendationResponse result = recommendations.recommend(buyer, 24);
        assertThat(result.mode()).isEqualTo("VEHICLE");
        assertThat(result.results()).extracting(i -> i.part().getId()).contains(matching.getId(), multiple.getId())
                .doesNotContain(incompatible.getId(), wrongFuel.getId());
        assertThat(result.results().getFirst().reasons()).contains("Listed for your Volkswagen Golf 2020");
        assertThat(recommendations.settings(other).vehicles()).isEmpty();
    }

    @Test void noMatchingVehicleDoesNotWidenResults() {
        listing("Clio only", clio, brakes);
        recommendations.updateSettings(buyer, new RecommendationSettingsRequest(true, List.of(golf.getId())));
        assertThat(recommendations.recommend(buyer, 6).results()).isEmpty();
    }

    @Test void savedCategoryRanksFirstButSavedExactListingIsExcluded() {
        Part saved = listing("Saved caliper", golf, brakes);
        Part related = listing("Related brake disc", golf, brakes);
        listing("Unrelated light", golf, lighting);
        Favorite favorite = new Favorite(); favorite.setUser(buyer); favorite.setPart(saved); favorites.saveAndFlush(favorite);
        RecommendationResponse result = recommendations.recommend(buyer, 24);
        assertThat(result.results().getFirst().part().getId()).isEqualTo(related.getId());
        assertThat(result.results().getFirst().reasons()).contains("Based on saved parts in this category");
        assertThat(result.results()).extracting(i -> i.part().getId()).doesNotContain(saved.getId());
        assertThat(recommendations.recommend(other, 24).mode()).isEqualTo("DISCOVER");
    }

    @Test void viewsAreDeduplicatedSearchAndContactAreCapturedAfterSuccessfulRequests() throws Exception {
        Part part = listing("UniqueRecommendationCaliper", golf, brakes);
        for (int i=0; i<3; i++) mvc.perform(get("/api/parts/{id}", part.getId()).with(user(buyer.getEmail()).roles("BUYER"))).andExpect(status().isOk());
        mvc.perform(get("/api/parts").param("search", "UniqueRecommendationCaliper").with(user(buyer.getEmail()).roles("BUYER"))).andExpect(status().isOk());
        mvc.perform(get("/api/sellers/{id}/contact", seller.getId()).with(user(buyer.getEmail()).roles("BUYER"))).andExpect(status().isOk());
        assertThat(activities.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId())).hasSize(3)
                .extracting(BuyerInteraction::getKind).containsExactlyInAnyOrder(BuyerInteraction.Kind.VIEW, BuyerInteraction.Kind.SEARCH, BuyerInteraction.Kind.CONTACT);
        mvc.perform(get("/api/parts/{id}", part.getId())).andExpect(status().isOk());
        assertThat(activities.findByBuyerIdOrderByOccurredAtDescIdDesc(other.getId())).isEmpty();
    }

    @Test void clearAndOptOutForgetSignalsWithoutDeletingFavoritesOrConversations() throws Exception {
        Part saved = listing("Saved", golf, brakes);
        Favorite favorite = new Favorite(); favorite.setUser(buyer); favorite.setPart(saved); favorites.saveAndFlush(favorite);
        favorite.setCreatedAt(LocalDateTime.now().minusDays(1));
        Conversation conversation = new Conversation(); conversation.setBuyer(buyer); conversation.setSeller(seller); conversation.setPart(saved);
        conversations.saveAndFlush(conversation); conversation.setUpdatedAt(LocalDateTime.now().minusDays(1));
        activity.record(buyer.getId(), BuyerInteraction.Kind.VIEW, saved.getId(), brakes.getId(), seller.getId());
        mvc.perform(delete("/api/recommendations/history").with(user(buyer.getEmail()).roles("BUYER"))).andExpect(status().isNoContent());
        assertThat(activities.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId())).isEmpty();
        assertThat(recommendations.recommend(buyer, 6).mode()).isEqualTo("DISCOVER");
        assertThat(favorites.existsByUserIdAndPartId(buyer.getId(), saved.getId())).isTrue();
        assertThat(conversations.findByPartIdAndBuyerId(saved.getId(), buyer.getId())).isPresent();
        recommendations.updateSettings(buyer, new RecommendationSettingsRequest(false, List.of(golf.getId())));
        activity.record(buyer.getId(), BuyerInteraction.Kind.VIEW, saved.getId(), brakes.getId(), seller.getId());
        assertThat(activities.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId())).isEmpty();
        assertThat(recommendations.recommend(buyer, 6).mode()).isEqualTo("VEHICLE");
    }

    @Test void contactAndMessagingBoostSellerWithoutReadingMessageBodies() {
        Part contacted = listing("Contacted part", golf, brakes);
        Part alternative = listing("Same seller alternative", golf, lighting);
        User secondSeller = account("recommend-second-seller@test.local", UserRole.SELLER);
        Part different = listing("Different seller", golf, lighting); different.setSeller(secondSeller);
        Conversation c = new Conversation(); c.setBuyer(buyer); c.setSeller(seller); c.setPart(contacted); conversations.saveAndFlush(c);
        var result = recommendations.recommend(buyer, 24);
        assertThat(result.results().getFirst().part().getId()).isEqualTo(alternative.getId());
        assertThat(result.results().getFirst().reasons()).contains("From a seller you messaged");
        assertThat(result.results()).extracting(i -> i.part().getId()).doesNotContain(contacted.getId());
    }

    @Test void oldSignalsExpireAndHistoryIsBounded() {
        BuyerInteraction old = new BuyerInteraction(); old.setBuyer(buyer); old.setKind(BuyerInteraction.Kind.VIEW);
        old.setTargetId(99999L); old.setCategoryId(brakes.getId()); old.setOccurredAt(LocalDateTime.now().minusDays(91)); activities.saveAndFlush(old);
        assertThat(recommendations.recommend(buyer, 6).mode()).isEqualTo("DISCOVER");
        for (long id = 1; id <= 202; id++) activity.record(buyer.getId(), BuyerInteraction.Kind.VIEW, id, brakes.getId(), seller.getId());
        assertThat(activities.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId())).hasSize(200)
                .allMatch(row -> row.getOccurredAt().isAfter(LocalDateTime.now().minusDays(90)));
    }

    @Test void savedVehicleFieldsCannotMatchAcrossDifferentCompatibilityRows() {
        Vehicle oldGolf = vehicles.save(new Vehicle(null, "Volkswagen", "Golf", 2019, "1.6 TDI", "Diesel"));
        Part mixed = listing("Wrong year Golf plus 2020 Clio", oldGolf, brakes);
        mixed.getCompatibleVehicles().add(oldGolf); mixed.getCompatibleVehicles().add(clio);
        recommendations.updateSettings(buyer, new RecommendationSettingsRequest(true, List.of(golf.getId())));
        assertThat(recommendations.recommend(buyer, 24).results()).extracting(i -> i.part().getId()).doesNotContain(mixed.getId());
    }

    @Test void deletingAViewedListingDoesNotBreakHistoryOrRecommendations() {
        Part p = listing("Deleted part", golf, brakes);
        activity.record(buyer.getId(), BuyerInteraction.Kind.VIEW, p.getId(), brakes.getId(), seller.getId());
        parts.delete(p); parts.flush();
        assertThat(recommendations.recommend(buyer, 24).results()).extracting(i -> i.part().getId()).doesNotContain(p.getId());
    }

    private User account(String email, UserRole role) {
        User u = new User(); u.setEmail(email); u.setPassword("not-a-login-fixture"); u.setFullName("Recommendation fixture"); u.setRole(role); return users.save(u);
    }
    private Part listing(String title, Vehicle vehicle, Category category) {
        Part p = new Part(); p.setTitle(title); p.setSeller(seller); p.setPrice(BigDecimal.TEN); p.setVehicle(vehicle); p.setCategory(category); return parts.saveAndFlush(p);
    }
}
