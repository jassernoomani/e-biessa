package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.UserProfileRequest;
import com.readdlytech.marketplaceapi.dto.UserResponse;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.entity.SellerApplicationStatus;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.exception.ConflictException;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public UserResponse updateProfile(User user, UserProfileRequest request) {
        user.setFullName(request.fullName().trim());
        applyProfile(user, request);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse requestSellerApproval(User user) {
        if (user.getRole() != UserRole.BUYER) throw new IllegalStateException("This account already has seller access");
        if (user.getSellerApplicationStatus() == SellerApplicationStatus.PENDING) {
            throw new ConflictException("Your seller application is already pending review");
        }
        if (blank(user.getPhone()) || blank(user.getLocation()) || blank(user.getBusinessName())) {
            throw new IllegalArgumentException("Add a phone number, location, and business name before applying");
        }
        user.setSellerApplicationStatus(SellerApplicationStatus.PENDING);
        user.setSellerApplicationSubmittedAt(java.time.LocalDateTime.now());
        user.setSellerApplicationReviewedAt(null);
        user.setSellerApplicationReviewedById(null);
        user.setSellerApplicationReviewNote(null);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public void applyProfile(User user, UserProfileRequest request) {
        String requestedPhone = phone(request.phone());
        if (!requestedPhone.isEmpty() && userRepository.findAll().stream()
                .anyMatch(existing -> !existing.getId().equals(user.getId()) && phone(existing.getPhone()).equals(requestedPhone))) {
            throw new ConflictException("Phone number already registered");
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(clean(request.phone()));
        user.setLocation(clean(request.location()));
        user.setBusinessName(clean(request.businessName()));
        user.setWebsite(clean(request.website()));
        user.setBio(clean(request.bio()));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String phone(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
}
