package com.readdlytech.marketplaceapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiSearchRateLimiter {
    private final int requestsPerWindow;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AiSearchRateLimiter(
            @Value("${app.ai.search.rate-limit.requests:12}") int requestsPerWindow,
            @Value("${app.ai.search.rate-limit.window-seconds:60}") long windowSeconds) {
        this(requestsPerWindow, windowSeconds, Clock.systemUTC());
    }

    AiSearchRateLimiter(int requestsPerWindow, long windowSeconds, Clock clock) {
        this.requestsPerWindow = Math.max(1, requestsPerWindow);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.clock = clock;
    }

    public void check(String clientKey) {
        long now = Instant.now(clock).getEpochSecond();
        Window window = windows.compute(clientKey, (ignored, current) -> {
            if (current == null || now - current.startedAtEpochSecond() >= windowSeconds) {
                return new Window(now, 1);
            }
            return new Window(current.startedAtEpochSecond(), current.requests() + 1);
        });
        if (window.requests() > requestsPerWindow) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI search rate limit exceeded. Try again shortly.");
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtEpochSecond() >= windowSeconds);
        }
    }

    private record Window(long startedAtEpochSecond, int requests) {}
}
