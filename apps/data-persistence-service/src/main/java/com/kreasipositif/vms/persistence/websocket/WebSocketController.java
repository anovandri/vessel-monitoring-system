package com.kreasipositif.vms.persistence.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket controller for handling client subscriptions and testing
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Health check endpoint for WebSocket service
     */
    @GetMapping("/api/websocket/health")
    @ResponseBody
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "WebSocket Real-time Updates");
        response.put("timestamp", Instant.now().toString());
        response.put("endpoints", Map.of(
            "websocket", "/ws",
            "topics", Map.of(
                "vessel-positions", "/topic/vessel-positions",
                "weather-data", "/topic/weather-data",
                "port-data", "/topic/port-data",
                "vessel-alerts", "/topic/vessel-alerts"
            )
        ));
        return response;
    }

    /**
     * Handle subscription requests from clients
     * This is optional - clients can subscribe directly to topics
     */
    @MessageMapping("/subscribe")
    @SendTo("/topic/subscriptions")
    public Map<String, String> handleSubscription(Map<String, String> request) {
        String topic = request.get("topic");
        log.info("Client subscribed to topic: {}", topic);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "subscribed");
        response.put("topic", topic);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
