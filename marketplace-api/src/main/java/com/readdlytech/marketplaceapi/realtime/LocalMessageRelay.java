package com.readdlytech.marketplaceapi.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!ha")
@RequiredArgsConstructor
public class LocalMessageRelay {
    private final MessageWebSocketHandler sockets;
    @TransactionalEventListener
    public void publish(RealtimeMessageEvent event) { sockets.deliver(event); }
}
