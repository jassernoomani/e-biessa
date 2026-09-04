package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.RealtimeTicketResponse;
import com.readdlytech.marketplaceapi.entity.User;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeTicketService {
    private static final int EXPIRY_SECONDS = 30;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    public RealtimeTicketResponse issue(User user) {
        removeExpired();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tickets.put(value, new Ticket(user.getId(), Instant.now().plusSeconds(EXPIRY_SECONDS)));
        return new RealtimeTicketResponse(value, EXPIRY_SECONDS);
    }

    public Optional<Long> consume(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        Ticket ticket = tickets.remove(value);
        if (ticket == null || ticket.expiresAt().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(ticket.userId());
    }

    private void removeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Ticket(Long userId, Instant expiresAt) {
    }
}
