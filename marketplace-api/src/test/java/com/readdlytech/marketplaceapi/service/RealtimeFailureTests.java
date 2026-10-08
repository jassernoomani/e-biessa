package com.readdlytech.marketplaceapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readdlytech.marketplaceapi.realtime.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeFailureTests {
    @Test void publishFailureDoesNotTurnCommittedMessageIntoAnError() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        when(redis.getRequiredConnectionFactory()).thenReturn(mock(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        when(redis.convertAndSend(anyString(), any())).thenThrow(new RedisConnectionFailureException("offline"));
        var sockets = mock(MessageWebSocketHandler.class);
        var relay = new RedisMessageRelay(redis, new ObjectMapper(), sockets);
        var event = new RealtimeMessageEvent(1L, null, 1L, 2L);
        try {
            assertDoesNotThrow(() -> relay.publish(event));
            verify(sockets).deliver(event);
        } finally { relay.destroy(); }
    }

    @Test void redisOutageRejectsTicketsWithoutLocalFallback() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("private detail"));
        var store = new RedisRealtimeTicketStore(redis);
        assertTrue(store.consume("ticket").isEmpty());
        var error = assertThrows(ResponseStatusException.class, () -> store.put("ticket", 1));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("private detail"));
    }

    @Test void socketsOnlyDeliverToEventParticipants() throws Exception {
        var handler = new MessageWebSocketHandler(new ObjectMapper());
        var buyer = mock(WebSocketSession.class);
        var stranger = mock(WebSocketSession.class);
        when(buyer.getAttributes()).thenReturn(Map.of(MessageSocketHandshakeInterceptor.USER_ID_ATTRIBUTE, 1L));
        when(stranger.getAttributes()).thenReturn(Map.of(MessageSocketHandshakeInterceptor.USER_ID_ATTRIBUTE, 3L));
        when(buyer.isOpen()).thenReturn(true);
        when(stranger.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(buyer); handler.afterConnectionEstablished(stranger);
        var message = new com.readdlytech.marketplaceapi.dto.MessageResponse(1L, 1L, "Test", "hello", null, null, java.util.List.of());
        handler.deliver(new RealtimeMessageEvent(1L, message, 1L, 2L));
        verify(buyer).sendMessage(any());
        verify(stranger, never()).sendMessage(any());
    }
}
