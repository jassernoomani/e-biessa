package com.readdlytech.marketplaceapi.realtime;

import com.readdlytech.marketplaceapi.security.TokenHash;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.Optional;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
@Profile("ha")
public class RedisRealtimeTicketStore implements RealtimeTicketStore {
    private final StringRedisTemplate redis;
    public RedisRealtimeTicketStore(StringRedisTemplate redis) { this.redis = redis; }
    public void put(String ticket, long userId) {
        try {
            redis.opsForValue().set(key(ticket), Long.toString(userId), Duration.ofSeconds(30));
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Live messaging is temporarily unavailable. Messages can still be refreshed.");
        }
    }
    public Optional<Long> consume(String ticket) {
        try {
            // GETDEL is atomic: only one replica may redeem the ticket.
            String value = redis.opsForValue().getAndDelete(key(ticket));
            return value == null ? Optional.empty() : Optional.of(Long.parseLong(value));
        } catch (org.springframework.dao.DataAccessException | NumberFormatException exception) {
            return Optional.empty(); // Fail closed; never switch to local authentication.
        }
    }
    private String key(String ticket) { return "e-biessa:realtime:ticket:" + TokenHash.sha256(ticket); }
}
