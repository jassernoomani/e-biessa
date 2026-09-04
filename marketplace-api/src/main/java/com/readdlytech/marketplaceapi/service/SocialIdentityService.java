package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.entity.ExternalIdentity;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.repository.ExternalIdentityRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialIdentityService {
    private final ExternalIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User findOrCreate(String provider, String subject, String email, String fullName) {
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("The social account did not provide a usable email address");
        }
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        return identityRepository.findByProviderAndProviderSubject(normalizedProvider, subject)
                .map(ExternalIdentity::getUser)
                .orElseGet(() -> link(normalizedProvider, subject, email, fullName));
    }

    private User link(String provider, String subject, String email, String fullName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(() -> {
            User created = new User();
            created.setEmail(normalizedEmail);
            created.setFullName(fullName == null || fullName.isBlank() ? normalizedEmail : fullName.trim());
            created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
            created.setRole(UserRole.BUYER);
            return userRepository.save(created);
        });
        ExternalIdentity identity = new ExternalIdentity();
        identity.setProvider(provider);
        identity.setProviderSubject(subject);
        identity.setUser(user);
        identityRepository.save(identity);
        return user;
    }
}
