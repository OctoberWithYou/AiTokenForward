package org.ljc.server;

import org.ljc.ConfigLoader;
import org.ljc.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AgentWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final AgentManager agentManager;
    private final String expectedToken;
    private final Set<PendingRequest> pendingRequests = ConcurrentHashMap.newKeySet();

    public AgentWebSocketHandler(AgentManager agentManager, String expectedToken) {
        this.agentManager = agentManager;
        this.expectedToken = expectedToken;
    }

    public void handleOpen(String sessionId, Message.WebSocketSession wsSession) {
        logger.info("Agent connected: {}", sessionId);
    }

    public Message handleMessage(String sessionId, String text, Message.WebSocketSession wsSession) {
        try {
            Message msg = ConfigLoader.fromJson(text, Message.class);
            logger.debug("Received message type: {} from agent: {}", msg.getType(), sessionId);

            switch (msg.getType()) {
                case Message.TYPE_REGISTER:
                    return handleRegister(sessionId, msg, wsSession);
                case Message.TYPE_RESPONSE:
                    return handleResponse(sessionId, msg);
                case Message.TYPE_PING:
                    return handlePing();
                default:
                    logger.warn("Unknown message type: {}", msg.getType());
                    return null;
            }
        } catch (IOException e) {
            logger.error("Error parsing message: {}", e.getMessage());
            return createErrorMessage(null, "Invalid message format");
        }
    }

    private Message handleRegister(String sessionId, Message msg, Message.WebSocketSession wsSession) {
        // 验证 token
        if (!expectedToken.equals(msg.getToken())) {
            logger.warn("Invalid token from agent: {}", sessionId);
            Message error = new Message();
            error.setType(Message.TYPE_ERROR);
            error.setError("Invalid token");
            return error;
        }

        // 注册 Agent
        AgentManager.AgentSession session = new AgentManager.AgentSession(sessionId, wsSession);
        if (!agentManager.registerAgent(sessionId, session)) {
            Message error = new Message();
            error.setType(Message.TYPE_ERROR);
            error.setError("Agent limit reached");
            return error;
        }

        // 注册支持的模型
        if (msg.getModels() != null) {
            for (Message.ModelInfo model : msg.getModels()) {
                session.addModel(model.getId());
            }
        }

        // 发送确认
        Message ack = new Message();
        ack.setType(Message.TYPE_REGISTER_ACK);
        ack.setError(null);
        logger.info("Agent registered successfully: {}, models: {}", sessionId, session.getSupportedModels());
        return ack;
    }

    private Message handleResponse(String sessionId, Message msg) {
        String requestId = msg.getRequestId();
        if (requestId != null) {
            // 找到等待这个响应的请求
            pendingRequests.removeIf(req -> req.requestId.equals(requestId));
        }
        return null; // 响应已经通过 HTTP 返回给客户端了
    }

    private Message handlePing() {
        Message pong = new Message();
        pong.setType(Message.TYPE_PONG);
        return pong;
    }

    public void handleClose(String sessionId) {
        agentManager.unregisterAgent(sessionId);
        logger.info("Agent disconnected: {}", sessionId);
    }

    public void handleError(String sessionId, Throwable t) {
        logger.error("Agent error: {}, error: {}", sessionId, t.getMessage());
        agentManager.unregisterAgent(sessionId);
    }

    public String createRequest(String model, String endpoint, Object payload) {
        String requestId = UUID.randomUUID().toString();

        Message request = new Message();
        request.setType(Message.TYPE_REQUEST);
        request.setRequestId(requestId);
        request.setModel(model);
        request.setEndpoint(endpoint);
        request.setPayload(payload);

        // 记录待处理请求
        PendingRequest pending = new PendingRequest(requestId, model);
        pendingRequests.add(pending);

        return requestId;
    }

    private Message createErrorMessage(String requestId, String error) {
        Message msg = new Message();
        msg.setType(Message.TYPE_ERROR);
        msg.setRequestId(requestId);
        msg.setError(error);
        return msg;
    }

    private static class PendingRequest {
        final String requestId;
        final String model;
        final long createdAt;

        PendingRequest(String requestId, String model) {
            this.requestId = requestId;
            this.model = model;
            this.createdAt = System.currentTimeMillis();
        }
    }
}