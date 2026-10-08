package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.RealtimeTicketResponse;
import com.readdlytech.marketplaceapi.entity.User;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import com.readdlytech.marketplaceapi.realtime.RealtimeTicketStore;

@Service
public class RealtimeTicketService {
    private static final int EXPIRY_SECONDS = 30;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RealtimeTicketStore tickets;

    public RealtimeTicketService(RealtimeTicketStore tickets) { this.tickets = tickets; }

    public RealtimeTicketResponse issue(User user) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tickets.put(value, user.getId());
        return new RealtimeTicketResponse(value, EXPIRY_SECONDS);
    }

    public Optional<Long> consume(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return tickets.consume(value);
    }

}
