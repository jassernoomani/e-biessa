package com.readdlytech.marketplaceapi.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        remove(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    public void deliver(RealtimeMessageEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(new SocketEnvelope(
                    "MESSAGE_CREATED", event.conversationId(), event.message()));
            send(event.buyerId(), payload);
            if (!event.sellerId().equals(event.buyerId())) send(event.sellerId(), payload);
        } catch (Exception exception) {
            log.warn("Could not serialize realtime message {}: {}", event.message().id(), exception.getMessage());
        }
    }

    private void send(Long userId, String payload) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) return;
        sessions.removeIf(session -> {
            if (!session.isOpen()) return true;
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                return false;
            } catch (IOException exception) {
                return true;
            }
        });
        if (sessions.isEmpty()) sessionsByUser.remove(userId, sessions);
    }

    private void remove(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionsByUser.remove(userId, sessions);
        }
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(MessageSocketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Long id ? id : null;
    }

    private record SocketEnvelope(String type, Long conversationId,
                                  com.readdlytech.marketplaceapi.dto.MessageResponse message) {
    }
}
