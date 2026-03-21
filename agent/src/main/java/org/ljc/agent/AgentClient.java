package org.ljc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.ljc.AgentConfigLoader;
import org.ljc.common.Message;
import org.ljc.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AgentClient {

    private static final Logger logger = LoggerFactory.getLogger(AgentClient.class);

    private final AgentConfig config;
    private final ModelCaller modelCaller;
    private final WebSocketClient webSocketClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public AgentClient(AgentConfig config) {
        this.config = config;
        this.modelCaller = new ModelCaller(config.getModels());
        this.webSocketClient = new WebSocketClient(this);
    }

    public void start() {
        logger.info("Starting agent...");
        webSocketClient.connect();
    }

    public void stop() {
        webSocketClient.disconnect();
        logger.info("Agent stopped");
    }

    public void onConnected() {
        try {
            // 发送注册消息
            Message register = new Message();
            register.setType(Message.TYPE_REGISTER);
            register.setToken(config.getServer().getToken());

            // 添加模型信息
            List<AgentConfig.ModelConfig> modelConfigs = config.getModels();
            if (modelConfigs != null) {
                List<Message.ModelInfo> modelInfos = new java.util.ArrayList<>();
                for (AgentConfig.ModelConfig mc : modelConfigs) {
                    Message.ModelInfo mi = new Message.ModelInfo();
                    mi.setId(mc.getId());
                    mi.setProvider(mc.getProvider());
                    mi.setEndpoint(mc.getEndpoint());
                    mi.setApiKey(mc.getApiKey());
                    mi.setApiVersion(mc.getApiVersion());
                    modelInfos.add(mi);
                }
                register.setModels(modelInfos);
            }

            String json = AgentConfigLoader.toJson(register);
            webSocketClient.send(json);

            logger.info("Registration message sent");
        } catch (IOException e) {
            logger.error("Failed to send registration: {}", e.getMessage());
        }
    }

    public void onMessage(String text) {
        try {
            Message msg = AgentConfigLoader.fromJson(text, Message.class);
            logger.debug("Received message type: {}", msg.getType());

            switch (msg.getType()) {
                case Message.TYPE_REGISTER_ACK:
                    logger.info("Registered successfully with server");
                    break;

                case Message.TYPE_REQUEST:
                    handleRequest(msg);
                    break;

                case Message.TYPE_PING:
                    Message pong = new Message();
                    pong.setType(Message.TYPE_PONG);
                    webSocketClient.send(AgentConfigLoader.toJson(pong));
                    break;

                case Message.TYPE_ERROR:
                    logger.error("Server error: {}", msg.getError());
                    break;

                default:
                    logger.warn("Unknown message type: {}", msg.getType());
            }
        } catch (IOException e) {
            logger.error("Error parsing message: {}", e.getMessage());
        }
    }

    private void handleRequest(Message request) {
        String requestId = request.getRequestId();
        String endpoint = request.getEndpoint();
        String model = request.getModel();
        Object payload = request.getPayload();

        logger.info("Received request: {} {} {}", requestId, endpoint, model);

        try {
            // 调用模型
            Object response = modelCaller.call(model, endpoint, payload);

            // 发送响应
            Message responseMsg = new Message();
            responseMsg.setType(Message.TYPE_RESPONSE);
            responseMsg.setRequestId(requestId);
            responseMsg.setStatus(200);
            responseMsg.setBody(response);

            webSocketClient.send(AgentConfigLoader.toJson(responseMsg));
            logger.info("Response sent for request: {}", requestId);
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage(), e);

            // 发送错误响应
            Message errorMsg = new Message();
            errorMsg.setType(Message.TYPE_RESPONSE);
            errorMsg.setRequestId(requestId);
            errorMsg.setStatus(500);
            errorMsg.setError(e.getMessage());

            try {
                webSocketClient.send(AgentConfigLoader.toJson(errorMsg));
            } catch (IOException ex) {
                logger.error("Failed to send error response: {}", ex.getMessage());
            }
        }
    }

    public void onDisconnected() {
        logger.warn("Disconnected from server");

        // 重连
        AgentConfig.ReconnectSettings reconnect = config.getReconnect();
        if (reconnect == null || reconnect.isEnabled()) {
            int maxAttempts = reconnect != null ? reconnect.getMaxAttempts() : 10;
            long initialDelay = reconnect != null ? reconnect.getInitialDelayMs() : 1000;
            long maxDelay = reconnect != null ? reconnect.getMaxDelayMs() : 60000;

            webSocketClient.reconnect(maxAttempts, initialDelay, maxDelay);
        }
    }

    public void onError(Throwable t) {
        logger.error("WebSocket error: {}", t.getMessage(), t);
    }

    public String getServerUrl() {
        return config.getServer().getUrl();
    }

    public interface MessageHandler {
        void onMessage(String message);
    }

    public static void main(String[] args) {
        String configPath = "config/agent-config.yaml";

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
                i++;
            }
        }

        try {
            AgentConfig config = AgentConfigLoader.loadAgentConfig(configPath);
            AgentClient agent = new AgentClient(config);
            agent.start();

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));

        } catch (Exception e) {
            logger.error("Failed to start agent: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}