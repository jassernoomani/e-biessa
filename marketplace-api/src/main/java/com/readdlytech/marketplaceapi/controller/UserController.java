package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.UserProfileRequest;
import com.readdlytech.marketplaceapi.dto.UserResponse;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final CurrentUserService currentUserService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(currentUserService.require(authentication)));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UserProfileRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.ok(userService.updateProfile(currentUserService.require(authentication), request));
    }

    @PostMapping("/me/become-seller")
    public ResponseEntity<UserResponse> becomeSeller(Authentication authentication) {
        return ResponseEntity.accepted().body(userService.requestSellerApproval(currentUserService.require(authentication)));
    }

}
