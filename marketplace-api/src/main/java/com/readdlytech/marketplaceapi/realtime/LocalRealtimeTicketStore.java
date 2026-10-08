package com.readdlytech.marketplaceapi.realtime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!ha")
public class LocalRealtimeTicketStore implements RealtimeTicketStore {
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    public void put(String value, long userId) {
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(Instant.now()));
        tickets.put(value, new Ticket(userId, Instant.now().plusSeconds(30)));
    }
    public Optional<Long> consume(String value) {
        Ticket ticket = tickets.remove(value);
        return ticket == null || !ticket.expiresAt().isAfter(Instant.now())
                ? Optional.empty() : Optional.of(ticket.userId());
    }
    private record Ticket(long userId, Instant expiresAt) {}
}
