package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.Message;
import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(Long id, Long senderId, String senderName, String body,
        LocalDateTime createdAt, LocalDateTime readAt, List<MessageAttachmentResponse> attachments) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getSender().getId(), message.getSender().getFullName(),
                message.getBody(), message.getCreatedAt(), message.getReadAt(),
                message.getAttachments().stream().map(MessageAttachmentResponse::from).toList());
    }
}
