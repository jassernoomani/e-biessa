package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.event.AiListingChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiListingEventListener {
    private final AiServiceClient aiServiceClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronize(AiListingChangedEvent event) {
        if (event.operation() == AiListingChangedEvent.Operation.UPSERT) {
            aiServiceClient.upsertQuietly(event.document());
        } else {
            aiServiceClient.deleteQuietly(event.listingId());
        }
    }
}

