package com.readdlytech.marketplaceapi.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiCircuitBreakersTests {
    @Test
    void failuresOpenCircuitAndRejectWithoutCallingUpstream() {
        AiCircuitBreakers circuits = new AiCircuitBreakers(3, 30);
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertThrows(ResourceAccessException.class, () -> circuits.execute("search", () -> {
                calls.incrementAndGet();
                throw new ResourceAccessException("timeout");
            }));
        }
        assertThrows(ResponseStatusException.class, () -> circuits.execute("search", () -> {
            calls.incrementAndGet(); return "unexpected";
        }));
        assertEquals(3, calls.get());
        assertEquals("ok", circuits.execute("vision", () -> "ok"));
        // Force half-open deterministically; no wall-clock sleeps in regression tests.
        circuits.breaker("search").transitionToHalfOpenState();
        assertEquals("recovered", circuits.execute("search", () -> "recovered"));
        assertEquals(CircuitBreaker.State.CLOSED, circuits.breaker("search").getState());
    }

    @Test
    void invalidInputsDoNotTripCircuitButEmptyResultsDo() {
        AiCircuitBreakers circuits = new AiCircuitBreakers(1, 30);
        assertThrows(HttpClientErrorException.class, () -> circuits.execute("vision", () -> {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        }));
        assertEquals(CircuitBreaker.State.CLOSED, circuits.breaker("vision").getState());
        assertThrows(ResourceAccessException.class, () -> circuits.execute("vision", () -> null));
        assertEquals(CircuitBreaker.State.OPEN, circuits.breaker("vision").getState());
        circuits.breaker("vision").transitionToHalfOpenState();
        assertThrows(ResourceAccessException.class, () -> circuits.execute("vision", () -> null));
        assertEquals(CircuitBreaker.State.OPEN, circuits.breaker("vision").getState());
    }
}
