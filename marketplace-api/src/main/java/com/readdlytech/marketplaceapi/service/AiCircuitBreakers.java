package com.readdlytech.marketplaceapi.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Per-instance protection, not a replacement for global AI admission control. */
@Component
public class AiCircuitBreakers {
    private final Map<String, CircuitBreaker> breakers;

    public AiCircuitBreakers(
            @Value("${app.ai.circuit.failure-window:3}") int window,
            @Value("${app.ai.circuit.open-seconds:30}") int openSeconds) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(Math.max(1, window))
                .minimumNumberOfCalls(Math.max(1, window))
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(Math.max(1, openSeconds)))
                .permittedNumberOfCallsInHalfOpenState(1)
                // Cold inference can take up to the configured HTTP read timeout.
                .slowCallDurationThreshold(Duration.ofMinutes(5))
                .ignoreException(error -> error instanceof RestClientResponseException response
                        && response.getStatusCode().is4xxClientError()
                        && response.getStatusCode().value() != 429)
                .build();
        breakers = Map.of("search", CircuitBreaker.of("ai-search", config),
                "assistant", CircuitBreaker.of("ai-assistant", config),
                "vision", CircuitBreaker.of("ai-vision", config));
    }

    public <T> T execute(String operation, Supplier<T> request) {
        try {
            return breaker(operation).executeSupplier(() -> {
                T response = request.get();
                if (response == null) throw new ResourceAccessException("Empty AI response");
                return response;
            });
        } catch (CallNotPermittedException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "This AI feature is temporarily paused after repeated failures. Try again shortly or use keyword search.");
        }
    }

    CircuitBreaker breaker(String operation) {
        CircuitBreaker breaker = breakers.get(operation);
        if (breaker == null) throw new IllegalArgumentException("Unknown AI operation");
        return breaker;
    }
}
