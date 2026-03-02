package com.kreasipositif.vms.persistence.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Redis Pub/Sub to WebSocket bridge
 * Listens to Redis channels and forwards messages to WebSocket clients
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisWebSocketBridge {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Configure Redis message listener container
     * Only for real-time critical data: vessel positions and alerts
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Register listeners for real-time data streams only
        container.addMessageListener(
            vesselPositionListener(), 
            new PatternTopic("vessel-positions-stream")
        );
        
        container.addMessageListener(
            vesselAlertListener(), 
            new PatternTopic("vessel-alerts-stream")
        );
        
        // Weather and port data removed - use REST API instead
        
        return container;
    }

    /**
     * Listener for vessel position updates
     */
    @Bean
    public MessageListenerAdapter vesselPositionListener() {
        return new MessageListenerAdapter(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String payload = new String(message.getBody());
                    log.debug("Forwarding vessel position to WebSocket: {}", payload);
                    messagingTemplate.convertAndSend("/topic/vessel-positions", payload);
                } catch (Exception e) {
                    log.error("Error forwarding vessel position to WebSocket", e);
                }
            }
        });
    }

    /**
     * Listener for vessel alert updates
     */
    @Bean
    public MessageListenerAdapter vesselAlertListener() {
        return new MessageListenerAdapter(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String payload = new String(message.getBody());
                    log.debug("Forwarding vessel alert to WebSocket: {}", payload);
                    messagingTemplate.convertAndSend("/topic/vessel-alerts", payload);
                } catch (Exception e) {
                    log.error("Error forwarding vessel alert to WebSocket", e);
                }
            }
        });
    }
}
