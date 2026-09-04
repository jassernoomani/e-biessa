package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.MessageAttachment;

public record MessageAttachmentResponse(Long id, String url, String originalName, String contentType, Long sizeBytes) {
    public static MessageAttachmentResponse from(MessageAttachment attachment) {
        return new MessageAttachmentResponse(attachment.getId(), attachment.getUrl(), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }
}
