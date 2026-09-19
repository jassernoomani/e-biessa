package com.readdlytech.marketplaceapi;

import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.repository.*;
import com.readdlytech.marketplaceapi.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceSecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PartRepository partRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired ImageRepository imageRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired PasswordRecoveryCodeRepository passwordRecoveryCodeRepository;
    @Autowired AuthExchangeCodeRepository authExchangeCodeRepository;
    @Autowired ExternalIdentityRepository externalIdentityRepository;
    @Autowired ListingReportRepository listingReportRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired MessageAttachmentRepository messageAttachmentRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired SellerReviewRepository sellerReviewRepository;
    @Autowired SellerReportRepository sellerReportRepository;
    @Autowired AdminAuditLogRepository adminAuditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired com.readdlytech.marketplaceapi.service.RealtimeTicketService realtimeTicketService;

    @BeforeEach
    void resetDatabase() {
        sellerReportRepository.deleteAll();
        sellerReviewRepository.deleteAll();
        messageAttachmentRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        adminAuditLogRepository.deleteAll();
        passwordRecoveryCodeRepository.deleteAll();
        authExchangeCodeRepository.deleteAll();
        externalIdentityRepository.deleteAll();
        listingReportRepository.deleteAll();
        favoriteRepository.deleteAll();
        imageRepository.deleteAll();
        partRepository.deleteAll();
        categoryRepository.deleteAll();
        vehicleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void publicVehicleCatalogFallsBackToNormalizedListingVehicles() throws Exception {
        vehicleRepository.save(new Vehicle(null, "Volkswagen", "Golf", 2018, "1.6 TDI", "Diesel"));
        vehicleRepository.save(new Vehicle(null, "Volkswagen", "Passat", 2018, "2.0 TDI", "Diesel"));

        mockMvc.perform(get("/api/vehicles/catalog/makes").param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("volkswagen"))
                .andExpect(jsonPath("$[0].name").value("Volkswagen"));

        mockMvc.perform(get("/api/vehicles/catalog/models")
                        .param("makeId", "volkswagen").param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Golf"))
                .andExpect(jsonPath("$[1]").value("Passat"));

        mockMvc.perform(get("/api/vehicles/catalog/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").isNumber());
    }

    @Test
    void vinDecodingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/vehicles/vin/WVWZZZ1KZAW000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void administratorCanSuspendAnAccountAndAuditTheAction() throws Exception {
        User admin = user("admin@example.com", UserRole.ADMIN);
        User seller = user("blocked-seller@example.com", UserRole.SELLER);
        userRepository.save(admin);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Seller listing", seller, null));

        mockMvc.perform(patch("/api/admin/users/{id}/suspension", seller.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspended\":true,\"reason\":\"Repeated prohibited listings\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspensionReason").value("Repeated prohibited listings"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked-seller@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("suspended")));
        org.assertj.core.api.Assertions.assertThat(partRepository.findById(listing.getId()).orElseThrow().getStatus())
                .isEqualTo(PartStatus.INACTIVE);
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("USER_SUSPENDED"))
                .andExpect(jsonPath("$[0].targetLabel").value("blocked-seller@example.com"));
    }

    @Test
    void suspensionInvalidatesAnAlreadyIssuedToken() throws Exception {
        User buyer = user("token-buyer@example.com", UserRole.BUYER);
        userRepository.save(buyer);
        String token = jwtUtil.generateToken(org.springframework.security.core.userdetails.User
                .withUsername(buyer.getEmail()).password(buyer.getPassword()).roles("BUYER").build());
        buyer.setSuspended(true);
        buyer.setSuspensionReason("Security review");
        userRepository.save(buyer);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreateOnlyOneUsableRealtimeTicket() throws Exception {
        User buyer = user("realtime-buyer@example.com", UserRole.BUYER);
        userRepository.save(buyer);

        String response = mockMvc.perform(post("/api/messages/conversations/realtime-ticket")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(buyer.getEmail()).roles("BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(30))
                .andReturn().getResponse().getContentAsString();

        String ticket = objectMapper.readTree(response).get("ticket").asText();
        org.assertj.core.api.Assertions.assertThat(realtimeTicketService.consume(ticket)).contains(buyer.getId());
        org.assertj.core.api.Assertions.assertThat(realtimeTicketService.consume(ticket)).isEmpty();
    }

    @Test
    void buyerAndSellerCanExchangePrivateListingMessages() throws Exception {
        User buyer = user("message-buyer@example.com", UserRole.BUYER);
        User seller = user("message-seller@example.com", UserRole.SELLER);
        User outsider = user("outsider@example.com", UserRole.BUYER);
        userRepository.save(buyer); userRepository.save(seller); userRepository.save(outsider);
        Part listing = partRepository.save(part("Golf alternator", seller, null));

        String created = mockMvc.perform(post("/api/messages/conversations")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(buyer.getEmail()).roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partId\":" + listing.getId() + ",\"message\":\"Is this still available?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[0].body").value("Is this still available?"))
                .andReturn().getResponse().getContentAsString();
        long conversationId = objectMapper.readTree(created).get("conversation").get("id").asLong();

        mockMvc.perform(get("/api/messages/conversations")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(seller.getEmail()).roles("SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[0].otherPartyName").value(buyer.getFullName()));
        mockMvc.perform(get("/api/messages/conversations/{id}", conversationId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(seller.getEmail()).roles("SELLER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/messages/conversations/{id}/messages", conversationId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(seller.getEmail()).roles("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Yes, collection is available.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Yes, collection is available."));

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile photo = new MockMultipartFile("files", "alternator-detail.png", "image/png", png);
        mockMvc.perform(multipart("/api/messages/conversations/{id}/messages", conversationId)
                        .file(photo).param("message", "Here is the connector close-up.")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(seller.getEmail()).roles("SELLER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Here is the connector close-up."))
                .andExpect(jsonPath("$.attachments[0].originalName").value("alternator-detail.png"))
                .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.attachments[0].url").value(org.hamcrest.Matchers.startsWith("/uploads/")));
        mockMvc.perform(get("/api/messages/conversations/{id}", conversationId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(outsider.getEmail()).roles("BUYER")))
                .andExpect(status().isForbidden());

        MockMultipartFile fakePhoto = new MockMultipartFile("files", "not-really.png", "image/png", "not-an-image".getBytes());
        mockMvc.perform(multipart("/api/messages/conversations/{id}/messages", conversationId)
                        .file(fakePhoto)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(buyer.getEmail()).roles("BUYER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not a valid PNG")));
    }

    @Test
    void publicCanBrowseButCannotPublish() throws Exception {
        mockMvc.perform(get("/api/parts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPartJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void priceGuidanceIsRestrictedToSellerAccounts() throws Exception {
        String request = "{\"title\":\"Golf brake caliper\",\"vehicleIds\":[]}";
        mockMvc.perform(post("/api/parts/price-estimate")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/parts/price-estimate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("buyer@example.com").roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/parts/price-estimate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("seller@example.com").roles("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("INSUFFICIENT"));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void listingUsesTheAuthenticatedSellerAndProfileNeverReturnsPassword() throws Exception {
        User seller = user("seller@example.com", UserRole.SELLER);
        userRepository.save(seller);

        mockMvc.perform(post("/api/parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPartJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(seller.getId()))
                .andExpect(jsonPath("$.sellerName").value("Test Seller"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("seller@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void buyerMustActivateSellerModeBeforePublishing() throws Exception {
        userRepository.save(user("buyer@example.com", UserRole.BUYER));
        mockMvc.perform(post("/api/parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPartJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void buyerCanViewAndUpdateOwnProfile() throws Exception {
        User buyer = user("buyer@example.com", UserRole.BUYER);
        userRepository.save(buyer);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Buyer\",\"phone\":\"+21622333444\",\"location\":\"Tunis, Tunisia\",\"bio\":\"Buyer profile biography\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Buyer"))
                .andExpect(jsonPath("$.phone").value("+21622333444"))
                .andExpect(jsonPath("$.location").value("Tunis, Tunisia"))
                .andExpect(jsonPath("$.bio").value("Buyer profile biography"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Buyer"));
    }

    @Test
    void marketplaceFiltersByBrandModelAndYear() throws Exception {
        User seller = user("seller@example.com", UserRole.SELLER);
        userRepository.save(seller);
        Vehicle golf = vehicle("Volkswagen", "Golf", 2018);
        Vehicle clio = vehicle("Renault", "Clio", 2019);
        vehicleRepository.save(golf);
        vehicleRepository.save(clio);
        partRepository.save(part("Golf headlight", seller, golf));
        partRepository.save(part("Clio mirror", seller, clio));

        mockMvc.perform(get("/api/parts")
                        .param("brand", "Volkswagen")
                        .param("model", "Golf")
                        .param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Golf headlight"));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCanPublishAndFilterARichMultiVehicleListing() throws Exception {
        User seller = user("seller@example.com", UserRole.SELLER);
        userRepository.save(seller);
        Vehicle golf = vehicle("Volkswagen", "Golf", 2018);
        golf.setEngine("1.6 TDI");
        golf.setFuelType("Diesel");
        Vehicle leon = vehicle("Seat", "Leon", 2019);
        leon.setEngine("1.6 TDI");
        leon.setFuelType("Diesel");
        vehicleRepository.save(golf);
        vehicleRepository.save(leon);

        String body = """
                {"title":"Interchangeable front caliper","description":"Bench tested","price":280,
                 "condition":"Used - good","vehicleIds":[%d,%d],"manufacturer":"ATE","partBrand":"ATE Original",
                 "oemNumber":"1K0615123","alternativeOemNumbers":["1K0615124","5Q0615123"],
                 "partPosition":"FRONT_LEFT","engineCompatibility":"1.6 TDI","fuelCompatibility":"Diesel",
                 "warrantyInformation":"90-day warranty","deliveryAvailable":true,"collectionAvailable":true,
                 "negotiable":true,"location":"Tunis"}
                """.formatted(golf.getId(), leon.getId());

        mockMvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compatibleVehicles.length()").value(2))
                .andExpect(jsonPath("$.manufacturer").value("ATE"))
                .andExpect(jsonPath("$.alternativeOemNumbers[1]").value("5Q0615123"))
                .andExpect(jsonPath("$.partPosition").value("FRONT_LEFT"))
                .andExpect(jsonPath("$.deliveryAvailable").value(true))
                .andExpect(jsonPath("$.negotiable").value(true));

        mockMvc.perform(get("/api/parts")
                        .param("brand", "Seat").param("model", "Leon")
                        .param("engine", "1.6 TDI").param("fuelType", "Diesel")
                        .param("partBrand", "ATE Original").param("partPosition", "FRONT_LEFT")
                        .param("warranty", "90-day").param("deliveryAvailable", "true")
                        .param("collectionAvailable", "true").param("negotiable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Interchangeable front caliper"));
    }

    @Test
    void publicSellerProfileShowsBusinessAndOnlyActiveListingsWithoutContactDetails() throws Exception {
        User seller = user("public-seller@example.com", UserRole.SELLER);
        seller.setPhone("+21620111222");
        seller.setBusinessName("Tunis Auto Parts");
        seller.setLocation("Tunis, Tunisia");
        seller.setWebsite("https://example.com");
        seller.setBio("Specialist in inspected original parts.");
        userRepository.save(seller);
        partRepository.save(part("Active headlight", seller, null));
        Part inactive = part("Hidden gearbox", seller, null);
        inactive.setStatus(PartStatus.INACTIVE);
        partRepository.save(inactive);

        mockMvc.perform(get("/api/sellers/{id}", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Tunis Auto Parts"))
                .andExpect(jsonPath("$.activeListingCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(get("/api/sellers/{id}/listings", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Active headlight"));

        mockMvc.perform(get("/api/sellers/{id}/contact", seller.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerMustContactSellerBeforeLeavingAReview() throws Exception {
        User buyer = user("review-buyer@example.com", UserRole.BUYER);
        User seller = user("review-seller@example.com", UserRole.SELLER);
        userRepository.save(buyer);
        userRepository.save(seller);
        partRepository.save(part("Reviewable listing", seller, null));

        mockMvc.perform(put("/api/sellers/{id}/reviews/me", seller.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(buyer.getEmail()).roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/sellers/{id}/reviews", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    void contactedBuyerCanCreateAndUpdateOneSellerReview() throws Exception {
        User buyer = user("verified-review-buyer@example.com", UserRole.BUYER);
        buyer.setFullName("Verified Buyer");
        User seller = user("verified-review-seller@example.com", UserRole.SELLER);
        userRepository.save(buyer);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Verified review listing", seller, null));
        Conversation conversation = new Conversation();
        conversation.setPart(listing);
        conversation.setBuyer(buyer);
        conversation.setSeller(seller);
        conversationRepository.save(conversation);

        var authenticatedBuyer = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(buyer.getEmail()).roles("BUYER");
        mockMvc.perform(put("/api/sellers/{id}/reviews/me", seller.getId())
                        .with(authenticatedBuyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.myReview.rating").value(5))
                .andExpect(jsonPath("$.myReview.comment").doesNotExist());

        mockMvc.perform(put("/api/sellers/{id}/reviews/me", seller.getId())
                        .with(authenticatedBuyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\" Helpful and responsive. \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.reviews[0].buyerName").value("Verified Buyer"))
                .andExpect(jsonPath("$.reviews[0].comment").value("Helpful and responsive."));

        org.assertj.core.api.Assertions.assertThat(sellerReviewRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/sellers/{id}", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void buyerCanReportSellerAndAdministratorCanSuspendTheAccount() throws Exception {
        User buyer = user("seller-report-buyer@example.com", UserRole.BUYER);
        buyer.setFullName("Concerned Buyer");
        User seller = user("reported-seller@example.com", UserRole.SELLER);
        seller.setBusinessName("Reported Garage");
        User admin = user("seller-report-admin@example.com", UserRole.ADMIN);
        userRepository.save(buyer);
        userRepository.save(seller);
        userRepository.save(admin);
        Part listing = partRepository.save(part("Reported seller listing", seller, null));
        var authenticatedBuyer = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(buyer.getEmail()).roles("BUYER");
        var authenticatedAdmin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(admin.getEmail()).roles("ADMIN");
        String reportBody = "{\"reason\":\"FRAUD_OR_SCAM\",\"details\":\"The seller requested payment using a suspicious external account.\"}";

        mockMvc.perform(post("/api/sellers/{id}/reports", seller.getId())
                        .with(authenticatedBuyer).contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerName").value(seller.getFullName()))
                .andExpect(jsonPath("$.businessName").value("Reported Garage"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/sellers/{id}/reports", seller.getId())
                        .with(authenticatedBuyer).contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isConflict());

        String queue = mockMvc.perform(get("/api/admin/seller-reports").param("status", "PENDING")
                        .with(authenticatedAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reporterName").value("Concerned Buyer"))
                .andReturn().getResponse().getContentAsString();
        long reportId = objectMapper.readTree(queue).get(0).get("id").asLong();
        mockMvc.perform(patch("/api/admin/seller-reports/{id}", reportId)
                        .with(authenticatedAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIONED\",\"adminNote\":\"Confirmed payment fraud after reviewing the conversation.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIONED"));

        User suspendedSeller = userRepository.findById(seller.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(suspendedSeller.getSuspended()).isTrue();
        org.assertj.core.api.Assertions.assertThat(suspendedSeller.getSuspensionReason()).contains("Confirmed payment fraud");
        org.assertj.core.api.Assertions.assertThat(partRepository.findById(listing.getId()).orElseThrow().getStatus())
                .isEqualTo(PartStatus.INACTIVE);
        mockMvc.perform(get("/api/admin/audit-logs").with(authenticatedAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SELLER_REPORT_RESOLVED"));
    }

    @Test
    @WithMockUser(username = "seller-reporter@example.com", roles = "SELLER")
    void sellerRoleCannotSubmitSellerReports() throws Exception {
        userRepository.save(user("seller-reporter@example.com", UserRole.SELLER));
        User reported = user("another-reported-seller@example.com", UserRole.SELLER);
        userRepository.save(reported);
        partRepository.save(part("Another seller listing", reported, null));
        mockMvc.perform(post("/api/sellers/{id}/reports", reported.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"details\":\"This report should not be accepted from a seller account.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void loggedInBuyerCanRevealSellerContactDetails() throws Exception {
        userRepository.save(user("buyer@example.com", UserRole.BUYER));
        User seller = user("contact-seller@example.com", UserRole.SELLER);
        seller.setPhone("+21620999888");
        seller.setBusinessName("Contact Garage");
        userRepository.save(seller);
        partRepository.save(part("Contact listing", seller, null));

        mockMvc.perform(get("/api/sellers/{id}/contact", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+21620999888"))
                .andExpect(jsonPath("$.email").value("contact-seller@example.com"))
                .andExpect(jsonPath("$.businessName").value("Contact Garage"));
    }

    @Test
    @WithMockUser(username = "another-seller@example.com", roles = "SELLER")
    void sellerRoleCannotRevealAnotherSellersContactDetails() throws Exception {
        userRepository.save(user("another-seller@example.com", UserRole.SELLER));
        User seller = user("private-seller@example.com", UserRole.SELLER);
        seller.setPhone("+21620000111");
        userRepository.save(seller);
        partRepository.save(part("Private contact listing", seller, null));

        mockMvc.perform(get("/api/sellers/{id}/contact", seller.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCanUploadChoosePrimaryAndDeleteOwnImages() throws Exception {
        User seller = user("seller@example.com", UserRole.SELLER);
        userRepository.save(seller);
        Part part = partRepository.save(part("Brake assembly", seller, null));
        MockMultipartFile first = new MockMultipartFile("files", "front.jpg", "image/jpeg", "first-image".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "rear.png", "image/png", "second-image".getBytes());

        mockMvc.perform(multipart("/api/parts/{id}/images", part.getId()).file(first).file(second))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].primary").value(false));

        var images = imageRepository.findByPartId(part.getId());
        mockMvc.perform(put("/api/parts/{partId}/images/order", part.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[" + images.get(1).getId() + "," + images.get(0).getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(images.get(1).getId()))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[0].sortOrder").value(0));

        mockMvc.perform(put("/api/parts/{partId}/images/{imageId}/primary", part.getId(), images.get(1).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(images.get(1).getId()))
                .andExpect(jsonPath("$[0].primary").value(true));

        mockMvc.perform(delete("/api/parts/{partId}/images/{imageId}", part.getId(), images.get(0).getId()))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(imageRepository.findByPartId(part.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void buyerCanReportAListingOnlyOnce() throws Exception {
        User buyer = user("buyer@example.com", UserRole.BUYER);
        User seller = user("reported-seller@example.com", UserRole.SELLER);
        userRepository.save(buyer);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Suspicious listing", seller, null));
        String report = "{\"reason\":\"WRONG_INFORMATION\",\"details\":\"The OEM number does not match the description\"}";

        mockMvc.perform(post("/api/parts/{id}/reports", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("WRONG_INFORMATION"));
        mockMvc.perform(post("/api/parts/{id}/reports", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void administratorCanActionAReportAndHideTheListing() throws Exception {
        User admin = user("admin@example.com", UserRole.ADMIN);
        User buyer = user("reporter@example.com", UserRole.BUYER);
        User seller = user("reported-seller@example.com", UserRole.SELLER);
        userRepository.save(admin);
        userRepository.save(buyer);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Reported listing", seller, null));
        ListingReport report = new ListingReport();
        report.setPart(listing);
        report.setReporter(buyer);
        report.setReason(ReportReason.SCAM);
        report.setStatus(ReportStatus.PENDING);
        report = listingReportRepository.save(report);

        mockMvc.perform(get("/api/admin/reports").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
        mockMvc.perform(patch("/api/admin/reports/{id}", report.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIONED\",\"adminNote\":\"Listing hidden during review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIONED"))
                .andExpect(jsonPath("$.partStatus").value("INACTIVE"));
        org.assertj.core.api.Assertions.assertThat(partRepository.findById(listing.getId()).orElseThrow().getStatus())
                .isEqualTo(PartStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void soldListingDisappearsFromPublicSurfacesImmediately() throws Exception {
        User seller = user("seller@example.com", UserRole.SELLER);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Recently sold alternator", seller, null));

        mockMvc.perform(patch("/api/parts/{id}/status", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.soldAt").isString());

        mockMvc.perform(get("/api/parts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/parts/{id}", listing.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/parts/my-listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("SOLD"));
    }

    @Test
    @WithMockUser(username = "other-seller@example.com", roles = "SELLER")
    void sellerCannotMarkAnotherSellersListingAsSold() throws Exception {
        userRepository.save(user("other-seller@example.com", UserRole.SELLER));
        User owner = user("owner@example.com", UserRole.SELLER);
        userRepository.save(owner);
        Part listing = partRepository.save(part("Owners gearbox", owner, null));

        mockMvc.perform(patch("/api/parts/{id}/status", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void administratorCannotBypassSellerApplicationButCanModerateListings() throws Exception {
        User admin = user("admin@example.com", UserRole.ADMIN);
        User buyer = user("buyer@example.com", UserRole.BUYER);
        userRepository.save(admin);
        userRepository.save(buyer);
        Part listing = partRepository.save(part("Review listing", buyer, null));

        mockMvc.perform(patch("/api/admin/users/{id}/role", buyer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Approve this user through the seller application queue"));

        mockMvc.perform(patch("/api/admin/parts/{id}/status", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void administratorCanViewAndEditACompleteSellerProfile() throws Exception {
        User admin = user("admin@example.com", UserRole.ADMIN);
        User seller = user("managed-seller@example.com", UserRole.SELLER);
        userRepository.save(admin);
        userRepository.save(seller);

        mockMvc.perform(get("/api/admin/users/{id}", seller.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("managed-seller@example.com"));

        mockMvc.perform(put("/api/admin/users/{id}/profile", seller.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Managed Seller\",\"phone\":\"+21671000000\",\"location\":\"Sfax, Tunisia\",\"businessName\":\"Sfax Auto Parts\",\"website\":\"https://example.com\",\"bio\":\"OEM specialist\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Sfax Auto Parts"))
                .andExpect(jsonPath("$.location").value("Sfax, Tunisia"))
                .andExpect(jsonPath("$.website").value("https://example.com"));
    }

    @Test
    void buyerJourneyUsesRealRegistrationJwtAndProfileEndpoints() throws Exception {
        String token = registerAndGetToken("journey-buyer@example.com", "Journey Buyer");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BUYER"));

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Buyer Journey Updated\",\"phone\":\"+21620000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Buyer Journey Updated"));
    }

    @Test
    void recoveryResponsesDoNotRevealAccountsAndCodesAreSingleUse() throws Exception {
        User buyer = user("recover@example.com", UserRole.BUYER);
        buyer.setPhone("+216 20 555 444");
        userRepository.save(buyer);

        mockMvc.perform(post("/api/auth/recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"missing@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account matches, a recovery code has been sent."))
                .andExpect(jsonPath("$.developmentCode").doesNotExist());

        PasswordRecoveryCode code = new PasswordRecoveryCode();
        code.setUser(buyer);
        code.setChannel(RecoveryChannel.EMAIL);
        code.setCodeHash(passwordEncoder.encode("123456"));
        code.setCreatedAt(java.time.LocalDateTime.now());
        code.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        passwordRecoveryCodeRepository.save(code);

        String reset = "{\"identifier\":\"recover@example.com\",\"code\":\"123456\",\"newPassword\":\"NewPassword123!\"}";
        mockMvc.perform(post("/api/auth/recovery/complete").contentType(MediaType.APPLICATION_JSON).content(reset))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recover@example.com\",\"password\":\"NewPassword123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        mockMvc.perform(post("/api/auth/recovery/complete").contentType(MediaType.APPLICATION_JSON).content(reset))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oauthExchangeCodeIsShortLivedAndSingleUse() throws Exception {
        User buyer = user("oauth@example.com", UserRole.BUYER);
        userRepository.save(buyer);
        AuthExchangeCode code = new AuthExchangeCode();
        code.setUser(buyer);
        code.setCodeHash(com.readdlytech.marketplaceapi.security.TokenHash.sha256("one-time-code"));
        code.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(2));
        authExchangeCodeRepository.save(code);

        String request = "{\"code\":\"one-time-code\"}";
        mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("oauth@example.com"));
        mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(false));
    }

    @Test
    void sellerJourneyRequiresAdministratorApprovalBeforeListingUsingJwt() throws Exception {
        String token = registerAndGetToken("journey-seller@example.com", "Journey Seller");
        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Journey Seller","phone":"+21620000000","location":"Tunis",
                                 "businessName":"Journey Garage","website":"","bio":"Original parts specialist"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/me/become-seller").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.sellerApplicationStatus").value("PENDING"));

        mockMvc.perform(post("/api/parts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(validPartJson()))
                .andExpect(status().isForbidden());

        User admin = user("seller-review-admin@example.com", UserRole.ADMIN);
        userRepository.save(admin);
        User applicant = userRepository.findByEmail("journey-seller@example.com").orElseThrow();
        var authenticatedAdmin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(admin.getEmail()).roles("ADMIN");
        mockMvc.perform(get("/api/admin/seller-applications").with(authenticatedAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicant.getId()))
                .andExpect(jsonPath("$[0].businessName").value("Journey Garage"));

        mockMvc.perform(patch("/api/admin/users/{id}/role", applicant.getId())
                        .with(authenticatedAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/seller-applications/{id}", applicant.getId())
                        .with(authenticatedAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"reason\":\"Business details verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.sellerApplicationStatus").value("APPROVED"));

        String listingJson = mockMvc.perform(post("/api/parts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPartJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long partId = objectMapper.readTree(listingJson).get("id").asLong();

        MockMultipartFile photo = new MockMultipartFile("files", "journey.jpg", "image/jpeg", "journey-image".getBytes());
        mockMvc.perform(multipart("/api/parts/{id}/images", partId)
                        .file(photo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].primary").value(true));

        mockMvc.perform(get("/api/parts/my-listings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].images[0].id").isNumber());

        mockMvc.perform(get("/api/admin/audit-logs").with(authenticatedAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SELLER_APPLICATION_APPROVED"));
    }

    @Test
    void rejectedSellerApplicantRemainsBuyerAndCanApplyAgain() throws Exception {
        User buyer = user("rejected-applicant@example.com", UserRole.BUYER);
        buyer.setPhone("+21621111111");
        buyer.setLocation("Sousse");
        buyer.setBusinessName("Applicant Garage");
        userRepository.save(buyer);
        User admin = user("application-admin@example.com", UserRole.ADMIN);
        userRepository.save(admin);
        var authenticatedBuyer = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(buyer.getEmail()).roles("BUYER");
        var authenticatedAdmin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(admin.getEmail()).roles("ADMIN");

        mockMvc.perform(post("/api/users/me/become-seller").with(authenticatedBuyer))
                .andExpect(status().isAccepted());
        mockMvc.perform(patch("/api/admin/seller-applications/{id}", buyer.getId())
                        .with(authenticatedAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/seller-applications/{id}", buyer.getId())
                        .with(authenticatedAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"Add verifiable business information\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.sellerApplicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.sellerApplicationReviewNote").value("Add verifiable business information"));

        mockMvc.perform(post("/api/users/me/become-seller").with(authenticatedBuyer))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sellerApplicationStatus").value("PENDING"));
    }

    @Test
    void administratorJourneyLogsInAndModeratesUsingJwt() throws Exception {
        userRepository.save(user("journey-admin@example.com", UserRole.ADMIN));
        User seller = user("moderated-seller@example.com", UserRole.SELLER);
        userRepository.save(seller);
        Part listing = partRepository.save(part("Moderated listing", seller, null));

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"journey-admin@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("token").asText();

        mockMvc.perform(patch("/api/admin/parts/{id}/status", listing.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void publicSemanticSearchFailsClearlyWhenAiServiceIsDisabled() throws Exception {
        mockMvc.perform(get("/api/ai/search").param("query", "Golf diesel brake"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void marketplaceAssistantRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/ai/assistant").param("query", "recommend a Golf brake for me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void imageIdentificationRequiresAuthentication() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "part.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/ai/identify").file(image))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedImageIdentificationValidatesUploadAndFailsClearlyWhenAiIsDisabled() throws Exception {
        User buyer = userRepository.save(user("vision-buyer@example.com", UserRole.BUYER));
        var authentication = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(buyer.getEmail()).roles("BUYER");

        MockMultipartFile text = new MockMultipartFile("file", "part.txt", "text/plain", "not an image".getBytes());
        mockMvc.perform(multipart("/api/ai/identify").file(text).with(authentication))
                .andExpect(status().isBadRequest());

        MockMultipartFile image = new MockMultipartFile("file", "part.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/ai/identify").file(image).with(authentication))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void aiReindexRequiresAdministratorRole() throws Exception {
        mockMvc.perform(post("/api/admin/ai/reindex"))
                .andExpect(status().isForbidden());
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFullName("Test Seller");
        user.setRole(role);
        return user;
    }

    private String validPartJson() {
        return """
                {"title":"Brake caliper","description":"Clean used OEM part","price":120.00,
                 "condition":"GOOD","oemNumber":"OEM-123","location":"Tunis"}
                """;
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"fullName\":\"" + fullName + "\",\"phone\":\"+21620000000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private Vehicle vehicle(String brand, String model, int year) {
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(brand);
        vehicle.setModel(model);
        vehicle.setYear(year);
        return vehicle;
    }

    private Part part(String title, User seller, Vehicle vehicle) {
        Part part = new Part();
        part.setTitle(title);
        part.setDescription("Integration test listing");
        part.setPrice(BigDecimal.valueOf(150));
        part.setCondition("Used - good");
        part.setLocation("Tunis");
        part.setStatus(PartStatus.ACTIVE);
        part.setSeller(seller);
        part.setVehicle(vehicle);
        return part;
    }
}
