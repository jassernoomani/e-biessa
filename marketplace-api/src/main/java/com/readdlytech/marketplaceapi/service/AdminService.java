package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.UserResponse;
import com.readdlytech.marketplaceapi.dto.UserProfileRequest;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.entity.SellerApplicationStatus;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final PartRepository partRepository;
    private final PartService partService;
    private final UserService userService;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> sellerApplications() {
        return userRepository.findBySellerApplicationStatusOrderBySellerApplicationSubmittedAtAsc(
                SellerApplicationStatus.PENDING).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse decideSellerApplication(Long userId, boolean approved, String reason, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != UserRole.BUYER || user.getSellerApplicationStatus() != SellerApplicationStatus.PENDING) {
            throw new IllegalStateException("This seller application is no longer pending");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        user.setSellerApplicationStatus(approved ? SellerApplicationStatus.APPROVED : SellerApplicationStatus.REJECTED);
        user.setSellerApplicationReviewedAt(java.time.LocalDateTime.now());
        user.setSellerApplicationReviewedById(admin.getId());
        user.setSellerApplicationReviewNote(reason == null || reason.isBlank() ? null : reason.trim());
        if (approved) user.setRole(UserRole.SELLER);
        User saved = userRepository.save(user);
        auditService.record(admin, approved ? "SELLER_APPLICATION_APPROVED" : "SELLER_APPLICATION_REJECTED",
                "USER", user.getId(), user.getEmail(), approved ? "Seller access granted" : reason.trim());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateRole(Long userId, UserRole role, User admin) {
        if (admin.getId().equals(userId)) {
            throw new IllegalArgumentException("Administrators cannot change their own role");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserRole previous = user.getRole();
        if (previous == UserRole.BUYER && role == UserRole.SELLER
                && user.getSellerApplicationStatus() != SellerApplicationStatus.APPROVED) {
            throw new IllegalArgumentException("Approve this user through the seller application queue");
        }
        user.setRole(role);
        if (role == UserRole.SELLER) user.setSellerApplicationStatus(SellerApplicationStatus.APPROVED);
        else user.setSellerApplicationStatus(SellerApplicationStatus.NONE);
        auditService.record(admin, "USER_ROLE_CHANGED", "USER", user.getId(), user.getEmail(), previous + " → " + role);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse user(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UserProfileRequest request, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userService.applyProfile(user, request);
        auditService.record(admin, "USER_PROFILE_UPDATED", "USER", user.getId(), user.getEmail(), "Administrator updated profile information");
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateSuspension(Long userId, boolean suspended, String reason, User admin) {
        if (admin.getId().equals(userId)) throw new IllegalArgumentException("Administrators cannot suspend their own account");
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (suspended && (reason == null || reason.isBlank())) throw new IllegalArgumentException("A suspension reason is required");
        user.setSuspended(suspended);
        user.setSuspensionReason(suspended ? reason.trim() : null);
        user.setSuspendedAt(suspended ? java.time.LocalDateTime.now() : null);
        user.setSuspendedById(suspended ? admin.getId() : null);
        if (suspended) partRepository.findBySellerAndStatusOrderByCreatedAtDesc(user, PartStatus.ACTIVE)
                .forEach(part -> {
                    part.setStatus(PartStatus.INACTIVE);
                    partService.removeFromAiIndexAfterCommit(part.getId());
                });
        auditService.record(admin, suspended ? "USER_SUSPENDED" : "USER_REINSTATED", "USER", user.getId(), user.getEmail(),
                suspended ? reason.trim() : "Account access restored; listings remain inactive until reviewed");
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<PartResponse> parts(Pageable pageable) {
        return partRepository.findAll(pageable).map(part -> partService.toResponse(part, null));
    }

    @Transactional
    public PartResponse moderate(Long partId, PartStatus status, User admin) {
        PartResponse response = partService.updateStatus(partId, status, admin);
        auditService.record(admin, "LISTING_STATUS_CHANGED", "PART", partId, response.getTitle(), "Status set to " + status);
        return response;
    }

    @Transactional
    public void deletePart(Long partId, User admin) {
        Part part = partService.findPart(partId);
        auditService.record(admin, "LISTING_DELETED", "PART", partId, part.getTitle(), "Listing permanently deleted");
        partService.deletePart(partId, admin);
    }
}
