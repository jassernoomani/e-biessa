package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByPartIdAndBuyerId(Long partId, Long buyerId);
    List<Conversation> findByBuyerIdOrSellerIdOrderByUpdatedAtDesc(Long buyerId, Long sellerId);
    boolean existsByBuyerIdAndSellerId(Long buyerId, Long sellerId);
}
