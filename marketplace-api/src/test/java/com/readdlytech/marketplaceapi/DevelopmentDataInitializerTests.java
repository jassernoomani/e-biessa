package com.readdlytech.marketplaceapi;

import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.repository.ImageRepository;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "dev"})
class DevelopmentDataInitializerTests {

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Test
    void seedsSixtyCompleteListingsAcrossThreeSellersAndCarQueryVehicles() {
        List<Part> parts = partRepository.findAll();
        List<User> catalogSellers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.SELLER)
                .filter(user -> user.getEmail().matches("seller\\d{2}@e-biessa\\.tn"))
                .toList();

        List<User> catalogBuyers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.BUYER)
                .filter(user -> user.getEmail().matches("buyer\\d{2}@e-biessa\\.tn"))
                .toList();

        assertThat(parts).hasSize(60);
        assertThat(catalogSellers).hasSize(3);
        assertThat(catalogBuyers).hasSize(3);
        assertThat(parts.stream().map(part -> part.getVehicle().getBrand()).distinct().toList()).hasSize(10);
        assertThat(parts.stream().map(part -> part.getVehicle().getModel()).distinct().toList()).hasSize(20);
        assertThat(parts)
                .allSatisfy(part -> {
                    assertThat(part.getDescription()).isNotBlank();
                    assertThat(part.getOemNumber()).isNotBlank();
                    assertThat(part.getCategory()).isNotNull();
                    assertThat(part.getVehicle()).isNotNull();
                    assertThat(part.getSeller()).isNotNull();
                    if (part.getTitle().contains("Front brake caliper")) assertThat(part.getPartPosition()).isEqualTo("FRONT");
                    if (part.getTitle().contains("Rear tail light")) assertThat(part.getPartPosition()).isEqualTo("REAR");
                    if (part.getTitle().contains("Left door mirror")) assertThat(part.getPartPosition()).isEqualTo("LEFT");
                    assertThat(imageRepository.countByPartId(part.getId())).isEqualTo(1);
                });
        assertThat(catalogSellers)
                .allSatisfy(seller -> assertThat(partRepository.findBySellerOrderByCreatedAtDesc(seller)).hasSize(20));
    }
}
