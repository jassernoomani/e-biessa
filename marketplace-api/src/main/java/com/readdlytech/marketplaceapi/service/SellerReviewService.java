package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.SellerReviewRequest;
import com.readdlytech.marketplaceapi.dto.SellerReviewResponse;
import com.readdlytech.marketplaceapi.dto.SellerReviewSummaryResponse;
import com.readdlytech.marketplaceapi.entity.SellerReview;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.ConversationRepository;
import com.readdlytech.marketplaceapi.repository.SellerReviewRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerReviewService {
    private final SellerReviewRepository reviewRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SellerReviewSummaryResponse summary(Long sellerId, User viewer) {
        seller(sellerId);
        List<SellerReview> reviews = reviewRepository.findBySellerIdOrderByUpdatedAtDesc(sellerId);
        double average = reviews.stream().mapToInt(SellerReview::getRating).average().orElse(0);
        average = Math.round(average * 10.0) / 10.0;
        SellerReviewResponse mine = viewer == null ? null : reviewRepository
                .findByBuyerIdAndSellerId(viewer.getId(), sellerId)
                .map(SellerReviewResponse::from).orElse(null);
        boolean eligible = viewer != null && viewer.getRole() == UserRole.BUYER
                && conversationRepository.existsByBuyerIdAndSellerId(viewer.getId(), sellerId);
        return new SellerReviewSummaryResponse(average, reviews.size(), eligible, mine,
                reviews.stream().map(SellerReviewResponse::from).toList());
    }

    @Transactional
    public SellerReviewSummaryResponse save(Long sellerId, SellerReviewRequest request, User buyer) {
        User seller = seller(sellerId);
        if (buyer.getRole() != UserRole.BUYER) {
            throw new AccessDeniedException("Only buyers can review sellers");
        }
        if (!conversationRepository.existsByBuyerIdAndSellerId(buyer.getId(), sellerId)) {
            throw new AccessDeniedException("Message this seller before leaving a review");
        }
        SellerReview review = reviewRepository.findByBuyerIdAndSellerId(buyer.getId(), sellerId)
                .orElseGet(SellerReview::new);
        review.setBuyer(buyer);
        review.setSeller(seller);
        review.setRating(request.rating());
        review.setComment(clean(request.comment()));
        reviewRepository.save(review);
        return summary(sellerId, buyer);
    }

    public long count(Long sellerId) {
        return reviewRepository.countBySellerId(sellerId);
    }

    public double average(Long sellerId) {
        double average = reviewRepository.findBySellerIdOrderByUpdatedAtDesc(sellerId).stream()
                .mapToInt(SellerReview::getRating).average().orElse(0);
        return Math.round(average * 10.0) / 10.0;
    }

    private User seller(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        if (seller.getRole() != UserRole.SELLER && seller.getRole() != UserRole.ADMIN) {
            throw new ResourceNotFoundException("Seller not found");
        }
        return seller;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
