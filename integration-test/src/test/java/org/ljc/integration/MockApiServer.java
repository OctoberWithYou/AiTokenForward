package org.ljc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock AI API Server for integration testing
 * Simulates OpenAI-compatible API responses
 */
public class MockApiServer {
    private static final Logger logger = LoggerFactory.getLogger(MockApiServer.class);

    private final int port;
    private HttpServer server;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public MockApiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Chat completions endpoint
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                logger.info("Mock API received chat request: {}", requestBody);

                String response = createChatResponse();
                sendJsonResponse(exchange, response, 200);
            } catch (Exception e) {
                logger.error("Error handling chat request", e);
                sendJsonError(exchange, e.getMessage(), 500);
            }
        });

        // Embeddings endpoint
        server.createContext("/v1/embeddings", exchange -> {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                logger.info("Mock API received embeddings request: {}", requestBody);

                String response = createEmbeddingResponse();
                sendJsonResponse(exchange, response, 200);
            } catch (Exception e) {
                logger.error("Error handling embeddings request", e);
                sendJsonError(exchange, e.getMessage(), 500);
            }
        });

        // Models endpoint
        server.createContext("/v1/models", exchange -> {
            try {
                String response = createModelsResponse();
                sendJsonResponse(exchange, response, 200);
            } catch (Exception e) {
                logger.error("Error handling models request", e);
                sendJsonError(exchange, e.getMessage(), 500);
            }
        });

        server.setExecutor(null);
        server.start();
        logger.info("Mock API Server started on port {}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Mock API Server stopped");
        }
    }

    private String createChatResponse() throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "chatcmpl-mock-123");
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", "test-model");

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", "This is a mock response from the test server.");
        choice.put("message", message);

        choice.put("finish_reason", "stop");

        response.put("choices", new Object[]{choice});

        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 20);
        usage.put("total_tokens", 30);
        response.put("usage", usage);

        return jsonMapper.writeValueAsString(response);
    }

    private String createEmbeddingResponse() throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("object", "list");
        response.put("data", new Object[]{
            Map.of(
                "object", "embedding",
                "embedding", new float[]{0.1f, 0.2f, 0.3f},
                "index", 0
            )
        });
        response.put("model", "test-embedding");
        response.put("usage", Map.of(
            "prompt_tokens", 5,
            "total_tokens", 5
        ));

        return jsonMapper.writeValueAsString(response);
    }

    private String createModelsResponse() throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("object", "list");
        response.put("data", new Object[]{
            Map.of(
                "id", "test-model",
                "object", "model",
                "created", System.currentTimeMillis() / 1000,
                "owned_by", "test"
            )
        });

        return jsonMapper.writeValueAsString(response);
    }

    private void sendJsonResponse(com.sun.net.httpserver.HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendJsonError(com.sun.net.httpserver.HttpExchange exchange, String message, int statusCode) throws IOException {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        String response = jsonMapper.writeValueAsString(error);
        sendJsonResponse(exchange, response, statusCode);
    }
}