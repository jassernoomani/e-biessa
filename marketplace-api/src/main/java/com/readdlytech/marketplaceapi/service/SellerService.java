package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.SellerContactResponse;
import com.readdlytech.marketplaceapi.dto.SellerProfileResponse;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerService {
    private final UserRepository userRepository;
    private final PartRepository partRepository;
    private final PartService partService;
    private final SellerReviewService sellerReviewService;

    @Transactional(readOnly = true)
    public SellerProfileResponse getProfile(Long sellerId) {
        User seller = findSeller(sellerId);
        return new SellerProfileResponse(
                seller.getId(), seller.getFullName(), seller.getBusinessName(), seller.getLocation(),
                seller.getWebsite(), seller.getBio(), seller.getCreatedAt(),
                partRepository.countBySellerAndStatus(seller, PartStatus.ACTIVE),
                sellerReviewService.average(sellerId), sellerReviewService.count(sellerId));
    }

    @Transactional(readOnly = true)
    public List<PartResponse> getActiveListings(Long sellerId, Long viewerId) {
        User seller = findSeller(sellerId);
        return partRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .filter(partService::isPubliclyVisible)
                .map(part -> partService.toResponse(part, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerContactResponse getContact(Long sellerId) {
        User seller = findSeller(sellerId);
        return new SellerContactResponse(seller.getId(), seller.getFullName(), seller.getBusinessName(),
                seller.getPhone(), seller.getEmail());
    }

    private User findSeller(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        if (partRepository.findBySellerOrderByCreatedAtDesc(seller).stream().noneMatch(partService::isPubliclyVisible)) {
            throw new ResourceNotFoundException("Seller not found");
        }
        return seller;
    }
}
