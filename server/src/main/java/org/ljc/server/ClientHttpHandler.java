package org.ljc.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ljc.ConfigLoader;
import org.ljc.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ClientHttpHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientHttpHandler.class);

    private final AuthManager authManager;
    private final AgentManager agentManager;
    private final AgentWebSocketHandler agentHandler;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    // 等待响应的请求
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();

    public ClientHttpHandler(AuthManager authManager, AgentManager agentManager, AgentWebSocketHandler agentHandler) {
        this.authManager = authManager;
        this.agentManager = agentManager;
        this.agentHandler = agentHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        logger.debug("Received {} request: {}", method, path);

        // 验证外部客户端 token
        if (!validateClientAuth(exchange)) {
            sendError(exchange, 401, "Unauthorized: Invalid or missing token");
            return;
        }

        // 处理请求
        try {
            if (method.equals("GET") && path.equals("/v1/models")) {
                handleModelsList(exchange);
            } else if (method.equals("POST") && path.startsWith("/v1/chat/completions")) {
                handleChatCompletions(exchange);
            } else if (method.equals("POST") && path.startsWith("/v1/embeddings")) {
                handleEmbeddings(exchange);
            } else if (method.equals("GET") && path.equals("/health")) {
                handleHealth(exchange);
            } else {
                sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private boolean validateClientAuth(HttpExchange exchange) {
        if (!authManager.hasExternalToken()) {
            // 如果没有配置外部 token，则跳过验证
            return true;
        }

        String headerName = authManager.getHeaderName();
        String token = exchange.getRequestHeaders().getFirst(headerName);
        return authManager.validateExternalToken(token);
    }

    private void handleModelsList(HttpExchange exchange) throws IOException {
        if (!agentManager.hasAvailableAgent()) {
            sendError(exchange, 503, "No available agents");
            return;
        }

        // 返回支持的模型列表（从 Agent 获取）
        Map<String, Object> response = new HashMap<>();
        response.put("object", "list");
        response.put("data", new Object[]{
                Map.of("id", "gpt-4", "object", "model", "created", System.currentTimeMillis() / 1000, "owned_by", "openai")
        });

        sendJson(exchange, 200, response);
    }

    private void handleChatCompletions(HttpExchange exchange) throws IOException {
        if (!agentManager.hasAvailableAgent()) {
            sendError(exchange, 503, "No available agents");
            return;
        }

        // 读取请求体
        String requestBody = new String(exchange.getRequestBody().readAllBytes());
        JsonNode requestJson = jsonMapper.readTree(requestBody);

        // 提取 model
        String model = requestJson.has("model") ? requestJson.get("model").asText() : "gpt-4";

        // 获取 Agent
        AgentManager.AgentSession agent = agentManager.getAgentForModel(model);
        if (agent == null) {
            sendError(exchange, 503, "No available agent for model: " + model);
            return;
        }

        // 创建请求 ID 并等待响应
        String requestId = java.util.UUID.randomUUID().toString();
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            // 发送请求给 Agent
            agent.sendRequest(requestId, "/v1/chat/completions", jsonMapper.readValue(requestBody, Object.class));

            // 等待响应（超时 120 秒）
            Message response = responseFuture.get(120, TimeUnit.SECONDS);

            if (response != null && response.getBody() != null) {
                sendJson(exchange, 200, response.getBody());
            } else {
                sendError(exchange, 500, "No response from agent");
            }
        } catch (Exception e) {
            logger.error("Error waiting for response: {}", e.getMessage());
            sendError(exchange, 504, "Gateway Timeout: " + e.getMessage());
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    private void handleEmbeddings(HttpExchange exchange) throws IOException {
        if (!agentManager.hasAvailableAgent()) {
            sendError(exchange, 503, "No available agents");
            return;
        }

        // 读取请求体
        String requestBody = new String(exchange.getRequestBody().readAllBytes());
        JsonNode requestJson = jsonMapper.readTree(requestBody);

        // 提取 model
        String model = requestJson.has("model") ? requestJson.get("model").asText() : "text-embedding-ada-002";

        // 获取 Agent
        AgentManager.AgentSession agent = agentManager.getAgentForModel(model);
        if (agent == null) {
            // 尝试使用默认模型
            agent = agentManager.getAgentForModel(null);
        }

        if (agent == null) {
            sendError(exchange, 503, "No available agent");
            return;
        }

        // 创建请求 ID 并等待响应
        String requestId = java.util.UUID.randomUUID().toString();
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            // 发送请求给 Agent
            agent.sendRequest(requestId, "/v1/embeddings", jsonMapper.readValue(requestBody, Object.class));

            // 等待响应（超时 60 秒）
            Message response = responseFuture.get(60, TimeUnit.SECONDS);

            if (response != null && response.getBody() != null) {
                sendJson(exchange, 200, response.getBody());
            } else {
                sendError(exchange, 500, "No response from agent");
            }
        } catch (Exception e) {
            logger.error("Error waiting for embeddings response: {}", e.getMessage());
            sendError(exchange, 504, "Gateway Timeout: " + e.getMessage());
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("agents", agentManager.getAgentCount());
        sendJson(exchange, 200, response);
    }

    public void onAgentResponse(String requestId, Message response) {
        CompletableFuture<Message> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String json = jsonMapper.writeValueAsString(body);
        byte[] responseBytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", Map.of(
                "message", message,
                "type", "invalid_request_error",
                "code", status
        ));
        sendJson(exchange, status, error);
    }
}