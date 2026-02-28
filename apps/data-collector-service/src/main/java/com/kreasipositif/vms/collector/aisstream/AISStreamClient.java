package com.kreasipositif.vms.collector.aisstream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for AIS Stream (aisstream.io)
 * Connects to AIS Stream WebSocket API and receives real-time vessel data
 */
@Slf4j
public class AISStreamClient extends TextWebSocketHandler {

    private final String websocketUrl;
    private final String apiKey;
    private final List<BoundingBox> boundingBoxes;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<String> messageQueue;
    private final AtomicBoolean connected;
    private final AtomicInteger reconnectAttempts;
    private final int maxReconnectAttempts;
    private final long reconnectDelayMs;
    
    private WebSocketSession session;
    private WebSocketClient client;

    public AISStreamClient(
            String websocketUrl, 
            String apiKey, 
            List<BoundingBox> boundingBoxes,
            int maxReconnectAttempts,
            long reconnectDelayMs) {
        this.websocketUrl = websocketUrl;
        this.apiKey = apiKey;
        this.boundingBoxes = boundingBoxes;
        this.objectMapper = new ObjectMapper();
        this.messageQueue = new LinkedBlockingQueue<>(1000);
        this.connected = new AtomicBoolean(false);
        this.reconnectAttempts = new AtomicInteger(0);
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMs = reconnectDelayMs;
        this.client = new StandardWebSocketClient();
    }

    /**
     * Connect to AIS Stream WebSocket
     */
    public void connect() throws Exception {
        if (connected.get()) {
            log.warn("Already connected to AIS Stream");
            return;
        }

        log.info("Connecting to AIS Stream at {}", websocketUrl);
        
        try {
            session = client.execute(this, websocketUrl).get(30, TimeUnit.SECONDS);
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("✅ Connected to AIS Stream successfully");
        } catch (Exception e) {
            log.error("Failed to connect to AIS Stream: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Called after WebSocket connection is established
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established. Session ID: {}", session.getId());
        this.session = session;
        connected.set(true);
        
        // Send subscription message
        sendSubscription();
    }

    /**
     * Send subscription message to AIS Stream
     */
    private void sendSubscription() throws IOException {
        if (session == null || !session.isOpen()) {
            log.error("Cannot send subscription - session not open");
            return;
        }

        // Convert bounding boxes to AIS Stream format
        List<List<List<Double>>> boxes = boundingBoxes.stream()
                .map(bb -> List.of(
                        List.of(bb.getMinLon(), bb.getMinLat()),
                        List.of(bb.getMaxLon(), bb.getMaxLat())
                ))
                .toList();

        // Create subscription message
        var subscription = new SubscriptionMessage(apiKey, boxes);
        String json = objectMapper.writeValueAsString(subscription);

        log.info("📤 Sending subscription for {} areas: {}", 
                boundingBoxes.size(), 
                boundingBoxes.stream().map(BoundingBox::getName).toList());
        
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Handle incoming WebSocket messages
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        try {
            // Parse to check if it's an error message
            JsonNode root = objectMapper.readTree(payload);
            
            if (root.has("error")) {
                log.error("❌ AIS Stream error: {}", root.get("error").asText());
                return;
            }
            
            if (root.has("Message") && root.get("Message").asText().equals("APIKey is valid")) {
                log.info("✅ API Key validated successfully");
                return;
            }
            
            // Log vessel data received
            if (root.has("MetaData") || root.has("MessageType")) {
                log.info("🚢 Received AIS message - Type: {}, MMSI: {}", 
                        root.has("MessageType") ? root.get("MessageType").asText() : "Unknown",
                        root.has("MetaData") && root.get("MetaData").has("MMSI") 
                            ? root.get("MetaData").get("MMSI").asText() : "Unknown");
            }
            
            // Queue the message for processing
            if (!messageQueue.offer(payload)) {
                log.warn("⚠️ Message queue full, dropping message");
            } else {
                log.debug("📥 Queued message (queue size: {})", messageQueue.size());
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage());
            log.debug("Raw payload: {}", payload.substring(0, Math.min(200, payload.length())));
        }
    }

    /**
     * Handle WebSocket errors
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", exception.getMessage());
        connected.set(false);
        attemptReconnect();
    }

    /**
     * Handle WebSocket connection close
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.warn("WebSocket connection closed. Status: {} - {}", status.getCode(), status.getReason());
        connected.set(false);
        attemptReconnect();
    }

    /**
     * Attempt to reconnect with exponential backoff
     */
    private void attemptReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();
        
        if (attempts > maxReconnectAttempts) {
            log.error("Max reconnect attempts ({}) reached. Giving up.", maxReconnectAttempts);
            return;
        }

        long delay = reconnectDelayMs * attempts;
        log.info("Attempting reconnect #{} in {}ms", attempts, delay);

        try {
            Thread.sleep(delay);
            connect();
        } catch (Exception e) {
            log.error("Reconnect attempt #{} failed: {}", attempts, e.getMessage());
        }
    }

    /**
     * Poll for next message (blocking with timeout)
     */
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected.get() && session != null && session.isOpen();
    }

    /**
     * Disconnect from AIS Stream
     */
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
                log.info("Disconnected from AIS Stream");
            } catch (IOException e) {
                log.error("Error closing WebSocket session: {}", e.getMessage());
            }
        }
        connected.set(false);
    }

    /**
     * Subscription message format for AIS Stream API
     */
    private record SubscriptionMessage(
            String APIKey,
            List<List<List<Double>>> BoundingBoxes
    ) {}
}
