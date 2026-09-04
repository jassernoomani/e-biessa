package com.readdlytech.marketplaceapi.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSearchRateLimiterTests {
    @Test
    void rejectsOnlyTheClientThatExceedsItsWindow() {
        MutableClock clock = new MutableClock();
        AiSearchRateLimiter limiter = new AiSearchRateLimiter(2, 60, clock);

        limiter.check("ip:one");
        limiter.check("ip:one");
        assertThatThrownBy(() -> limiter.check("ip:one"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
        assertThatCode(() -> limiter.check("ip:two")).doesNotThrowAnyException();
    }

    @Test
    void opensANewWindowAfterTheConfiguredDuration() {
        MutableClock clock = new MutableClock();
        AiSearchRateLimiter limiter = new AiSearchRateLimiter(1, 10, clock);
        limiter.check("user:7");
        assertThatThrownBy(() -> limiter.check("user:7")).isInstanceOf(ResponseStatusException.class);

        clock.advanceSeconds(10);
        assertThatCode(() -> limiter.check("user:7")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
    }
}
