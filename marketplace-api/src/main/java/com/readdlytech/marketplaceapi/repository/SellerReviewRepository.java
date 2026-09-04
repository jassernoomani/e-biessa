package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.SellerReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerReviewRepository extends JpaRepository<SellerReview, Long> {
    List<SellerReview> findBySellerIdOrderByUpdatedAtDesc(Long sellerId);
    Optional<SellerReview> findByBuyerIdAndSellerId(Long buyerId, Long sellerId);
    long countBySellerId(Long sellerId);
}
