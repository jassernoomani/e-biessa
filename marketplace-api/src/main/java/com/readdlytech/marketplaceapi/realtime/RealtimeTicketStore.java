package com.readdlytech.marketplaceapi.realtime;

import java.util.Optional;

public interface RealtimeTicketStore {
    void put(String ticket, long userId);
    Optional<Long> consume(String ticket);
}
