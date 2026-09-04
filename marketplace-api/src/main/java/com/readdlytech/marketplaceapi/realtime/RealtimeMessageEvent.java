package com.readdlytech.marketplaceapi.realtime;

import com.readdlytech.marketplaceapi.dto.MessageResponse;

public record RealtimeMessageEvent(Long conversationId, MessageResponse message, Long buyerId, Long sellerId) {
}
