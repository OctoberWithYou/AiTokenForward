package org.ljc.agent;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClient.class);

    private final AgentClient agentClient;
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connected = false;
    private int reconnectAttempts = 0;

    public WebSocketClient(AgentClient agentClient) {
        this.agentClient = agentClient;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // 无限读取超时
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    public void connect() {
        String url = agentClient.getServerUrl();
        logger.info("Connecting to server: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("Connected to server");
                connected = true;
                reconnectAttempts = 0;
                agentClient.onConnected();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                agentClient.onMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("Closing connection: {} {}", code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("Connection closed: {} {}", code, reason);
                connected = false;
                agentClient.onDisconnected();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket failure: {}", t.getMessage());
                connected = false;
                agentClient.onError(t);
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
    }

    public void send(String message) {
        if (webSocket != null && connected) {
            webSocket.send(message);
        } else {
            logger.warn("Cannot send message: not connected");
        }
    }

    public void reconnect(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        if (reconnectAttempts >= maxAttempts) {
            logger.error("Max reconnection attempts reached");
            return;
        }

        reconnectAttempts++;
        long delay = Math.min(initialDelayMs * (long) Math.pow(2, reconnectAttempts - 1), maxDelayMs);

        logger.info("Reconnecting in {} ms (attempt {}/{})", delay, reconnectAttempts, maxAttempts);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        connect();
    }

    public boolean isConnected() {
        return connected;
    }
}