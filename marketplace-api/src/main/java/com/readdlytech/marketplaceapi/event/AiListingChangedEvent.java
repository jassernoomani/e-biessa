package com.readdlytech.marketplaceapi.event;

import com.readdlytech.marketplaceapi.dto.AiListingDocument;

public record AiListingChangedEvent(Operation operation, Long listingId, AiListingDocument document) {
    public enum Operation { UPSERT, DELETE }

    public static AiListingChangedEvent upsert(AiListingDocument document) {
        return new AiListingChangedEvent(Operation.UPSERT, document.id(), document);
    }

    public static AiListingChangedEvent delete(Long listingId) {
        return new AiListingChangedEvent(Operation.DELETE, listingId, null);
    }
}

