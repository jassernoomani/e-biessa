package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.BuyerInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuyerInteractionRepository extends JpaRepository<BuyerInteraction, Long> {
    List<BuyerInteraction> findByBuyerIdOrderByOccurredAtDescIdDesc(Long buyerId);
}
