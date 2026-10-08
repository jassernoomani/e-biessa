package com.readdlytech.marketplaceapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readdlytech.marketplaceapi.realtime.*;
import com.readdlytech.marketplaceapi.dto.MessageResponse;
import com.readdlytech.marketplaceapi.security.TokenHash;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in, dedicated lab Redis only; no Spring application or database is started. */
@EnabledIfEnvironmentVariable(named = "HA_REDIS_TEST_PORT", matches = "16379")
class RedisRealtimeTests {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    @BeforeEach void setup() {
        factory = new LettuceConnectionFactory("127.0.0.1", 16379);
        factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory);
        try (var connection = factory.getConnection()) { assertEquals("PONG", connection.ping()); }
    }
    @AfterEach void close() { factory.destroy(); }

    @Test void ticketsAreSharedExpiringAndAtomicallyConsumed() throws Exception {
        var first = new RedisRealtimeTicketStore(redis);
        var second = new RedisRealtimeTicketStore(redis);
        String ticket = UUID.randomUUID().toString();
        String key = "e-biessa:realtime:ticket:" + TokenHash.sha256(ticket);
        first.put(ticket, 123);
        Long ttl = redis.getExpire(key);
        assertTrue(ttl > 0 && ttl <= 30);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> first.consume(ticket));
            var b = pool.submit(() -> second.consume(ticket));
            assertEquals(1, (a.get().isPresent() ? 1 : 0) + (b.get().isPresent() ? 1 : 0));
        }
        assertTrue(second.consume(ticket).isEmpty());
        first.put(ticket, 123);
        redis.expire(key, Duration.ZERO);
        assertTrue(second.consume(ticket).isEmpty());
    }

    @Test void notificationsReachTwoInstances() throws Exception {
        var firstSockets = mock(MessageWebSocketHandler.class);
        var secondSockets = mock(MessageWebSocketHandler.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var first = new RedisMessageRelay(redis, mapper, firstSockets);
        var second = new RedisMessageRelay(redis, mapper, secondSockets);
        try {
            first.ensureSubscribed(); second.ensureSubscribed();
            var event = new RealtimeMessageEvent(123L,
                    new MessageResponse(456L, 1L, "Test buyer", "Test message", LocalDateTime.now(), null, List.of()), 1L, 2L);
            first.publish(event);
            verify(firstSockets, timeout(5000)).deliver(event);
            verify(secondSockets, timeout(5000)).deliver(event);
        } finally { first.destroy(); second.destroy(); }
    }
}
