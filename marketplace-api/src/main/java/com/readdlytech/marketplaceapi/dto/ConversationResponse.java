package com.readdlytech.marketplaceapi.dto;

import java.time.LocalDateTime;

public record ConversationResponse(Long id, Long partId, String partTitle, String partImageUrl,
        Long buyerId, String buyerName, Long sellerId, String sellerName, String otherPartyName,
        String lastMessage, LocalDateTime lastMessageAt, long unreadCount) {}
