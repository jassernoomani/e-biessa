package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.realtime.RedisRealtimeTicketStore;
import com.readdlytech.marketplaceapi.security.TokenHash;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisTicketStoreTests {
    @Test
    @SuppressWarnings("unchecked")
    void storesOnlyHashedKeysWithShortExpiryAndConsumesAtomically() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var store = new RedisRealtimeTicketStore(redis);
        String key = "e-biessa:realtime:ticket:" + TokenHash.sha256("test-ticket");
        store.put("test-ticket", 42L);
        verify(values).set(key, "42", Duration.ofSeconds(30));
        when(values.getAndDelete(key)).thenReturn("42", null, "invalid-user-id");
        assertEquals(Optional.of(42L), store.consume("test-ticket"));
        assertTrue(store.consume("test-ticket").isEmpty());
        assertTrue(store.consume("test-ticket").isEmpty());
        verify(values, times(3)).getAndDelete(key);
    }
}
