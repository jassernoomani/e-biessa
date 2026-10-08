package com.readdlytech.marketplaceapi.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** MySQL is durable; Pub/Sub only accelerates delivery. REST reconciles missed events. */
@Component
@Profile("ha")
@Slf4j
public class RedisMessageRelay implements DisposableBean {
    private static final String CHANNEL = "e-biessa:realtime:messages";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MessageWebSocketHandler sockets;
    private final RedisMessageListenerContainer listener;

    public RedisMessageRelay(StringRedisTemplate redis, ObjectMapper mapper, MessageWebSocketHandler sockets) {
        this.redis = redis;
        this.mapper = mapper;
        this.sockets = sockets;
        // Owned here rather than a lifecycle bean: initial Redis outage must not abort API startup.
        listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(redis.getRequiredConnectionFactory());
        listener.setRecoveryInterval(5000);
        listener.setMaxSubscriptionRegistrationWaitingTime(2000);
        listener.addMessageListener((message, pattern) -> {
            try {
                sockets.deliver(mapper.readValue(message.getBody(), RealtimeMessageEvent.class));
            } catch (Exception exception) {
                log.warn("Rejected invalid realtime notification");
            }
        }, new ChannelTopic(CHANNEL));
        listener.afterPropertiesSet();
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void ensureSubscribed() {
        if (listener.isRunning()) return;
        try { listener.start(); }
        catch (Exception exception) {
            listener.stop();
            log.warn("Realtime subscription unavailable; HTTP messages remain available");
        }
    }

    @TransactionalEventListener
    public void publish(RealtimeMessageEvent event) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(event));
        } catch (Exception exception) {
            // The DB commit already succeeded; do not invite duplicate sends with a failed response.
            log.warn("Realtime notification deferred to HTTP reconciliation");
            sockets.deliver(event);
        }
    }

    @Override
    public void destroy() throws Exception { listener.destroy(); }
}
