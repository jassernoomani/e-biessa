package com.readdlytech.marketplaceapi.service;



import com.readdlytech.marketplaceapi.dto.AuthResponse;
import com.readdlytech.marketplaceapi.dto.LoginRequest;
import com.readdlytech.marketplaceapi.dto.RegisterRequest;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.exception.ConflictException;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import com.readdlytech.marketplaceapi.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        String normalizedPhone = phone(request.getPhone());
        if (!normalizedPhone.isEmpty() && userRepository.findAll().stream()
                .anyMatch(existing -> phone(existing.getPhone()).equals(normalizedPhone))) {
            throw new ConflictException("Phone number already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setRole(UserRole.BUYER);

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                )
        );

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return responseFor(user);
    }

    public AuthResponse responseFor(User user) {
        if (Boolean.TRUE.equals(user.getSuspended())) throw new DisabledException("This account is suspended");
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        return new AuthResponse(jwtUtil.generateToken(details), user.getId(), user.getEmail(),
                user.getFullName(), user.getRole().name());
    }

    private String phone(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
}
