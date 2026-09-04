package com.readdlytech.marketplaceapi.controller;


import com.readdlytech.marketplaceapi.dto.AuthResponse;
import com.readdlytech.marketplaceapi.dto.LoginRequest;
import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.service.AuthExchangeService;
import com.readdlytech.marketplaceapi.service.PasswordRecoveryService;
import org.springframework.beans.factory.annotation.Value;
import com.readdlytech.marketplaceapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthExchangeService authExchangeService;
    private final PasswordRecoveryService passwordRecoveryService;
    @Value("${app.oauth.enabled:false}") private boolean oauthEnabled;
    @Value("${app.oauth.google.client-id:}") private String googleId;
    @Value("${app.oauth.facebook.client-id:}") private String facebookId;
    @Value("${app.oauth.microsoft.client-id:}") private String microsoftId;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(authExchangeService.exchange(request.code()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/providers")
    public ResponseEntity<OAuthProvidersResponse> providers() {
        return ResponseEntity.ok(new OAuthProvidersResponse(oauthEnabled && !googleId.isBlank(),
                oauthEnabled && !facebookId.isBlank(), oauthEnabled && !microsoftId.isBlank()));
    }

    @PostMapping("/recovery/request")
    public ResponseEntity<RecoveryStartResponse> startRecovery(@Valid @RequestBody RecoveryStartRequest request) {
        return ResponseEntity.ok(passwordRecoveryService.start(request.identifier()));
    }

    @PostMapping("/recovery/complete")
    public ResponseEntity<Void> completeRecovery(@Valid @RequestBody RecoveryCompleteRequest request) {
        passwordRecoveryService.complete(request);
        return ResponseEntity.noContent().build();
    }
}
