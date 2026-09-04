package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.AuthResponse;
import com.readdlytech.marketplaceapi.entity.AuthExchangeCode;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.repository.AuthExchangeCodeRepository;
import com.readdlytech.marketplaceapi.security.TokenHash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthExchangeService {
    private final AuthExchangeCodeRepository repository;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String create(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthExchangeCode exchange = new AuthExchangeCode();
        exchange.setCodeHash(TokenHash.sha256(raw));
        exchange.setUser(user);
        exchange.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        repository.save(exchange);
        return raw;
    }

    @Transactional
    public AuthResponse exchange(String rawCode) {
        AuthExchangeCode exchange = repository.findByCodeHash(TokenHash.sha256(rawCode.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired sign-in code"));
        if (exchange.getUsedAt() != null || exchange.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired sign-in code");
        }
        exchange.setUsedAt(LocalDateTime.now());
        return authService.responseFor(exchange.getUser());
    }
}
