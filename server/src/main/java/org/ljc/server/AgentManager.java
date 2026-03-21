package org.ljc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.ljc.ConfigLoader;
import org.ljc.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AgentManager {

    private static final Logger logger = LoggerFactory.getLogger(AgentManager.class);

    private final Map<String, AgentSession> agents = new ConcurrentHashMap<>();
    private final String agentToken;
    private final int maxAgents;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public AgentManager(String agentToken, int maxAgents) {
        this.agentToken = agentToken;
        this.maxAgents = maxAgents;
    }

    public synchronized boolean registerAgent(String sessionId, AgentSession session) {
        if (agents.size() >= maxAgents) {
            logger.warn("Agent limit reached, cannot register: {}", sessionId);
            return false;
        }

        agents.put(sessionId, session);
        logger.info("Agent registered: {}, total agents: {}", sessionId, agents.size());
        return true;
    }

    public synchronized void unregisterAgent(String sessionId) {
        agents.remove(sessionId);
        logger.info("Agent unregistered: {}, remaining agents: {}", sessionId, agents.size());
    }

    public AgentSession getAgentForModel(String modelId) {
        // 简单轮询策略
        if (agents.isEmpty()) {
            return null;
        }

        // 返回第一个可用的 Agent
        return agents.values().stream().findFirst().orElse(null);
    }

    public boolean hasAvailableAgent() {
        return !agents.isEmpty();
    }

    public int getAgentCount() {
        return agents.size();
    }

    public static class AgentSession {
        private final String sessionId;
        private final Message.WebSocketSession wsSession;
        private final java.util.Set<String> supportedModels = ConcurrentHashMap.newKeySet();

        public AgentSession(String sessionId, Message.WebSocketSession wsSession) {
            this.sessionId = sessionId;
            this.wsSession = wsSession;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Message.WebSocketSession getWsSession() {
            return wsSession;
        }

        public void addModel(String modelId) {
            supportedModels.add(modelId);
        }

        public java.util.Set<String> getSupportedModels() {
            return supportedModels;
        }

        public boolean supportsModel(String modelId) {
            return supportedModels.isEmpty() || supportedModels.contains(modelId);
        }

        public void sendRequest(String requestId, String endpoint, Object payload) throws IOException {
            Message request = new Message();
            request.setType(Message.TYPE_REQUEST);
            request.setRequestId(requestId);
            request.setEndpoint(endpoint);
            request.setPayload(payload);

            String json = ConfigLoader.toJson(request);
            wsSession.sendText(json);
        }

        public Message waitForResponse(String requestId, long timeoutMs) throws InterruptedException, IOException {
            final CountDownLatch latch = new CountDownLatch(1);
            final Message[] responseHolder = new Message[1];

            // 创建一个简单的响应等待器
            // 实际实现中应该使用更复杂的方式
            synchronized (this) {
                // 简化的实现：使用轮询
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    Thread.sleep(100);
                    // 这里需要检查是否有响应到达
                    // 实际实现中应该在收到消息时检查 requestId 并通知
                }
            }

            return null; // 简化实现
        }
    }
}