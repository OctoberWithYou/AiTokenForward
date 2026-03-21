package org.ljc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.ljc.config.AgentConfig;
import org.ljc.common.Message;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 核心功能测试
 */
class AgentCoreTest {

    private AgentConfig config;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        config = new AgentConfig();

        AgentConfig.ServerSettings serverSettings = new AgentConfig.ServerSettings();
        serverSettings.setUrl("wss://test.example.com/agent");
        serverSettings.setToken("test-token");
        config.setServer(serverSettings);

        AgentConfig.ModelConfig modelConfig = new AgentConfig.ModelConfig();
        modelConfig.setId("gpt-4");
        modelConfig.setProvider("openai");
        modelConfig.setEndpoint("https://api.openai.com/v1");
        modelConfig.setApiKey("sk-test-key");
        config.setModels(List.of(modelConfig));

        AgentConfig.ReconnectSettings reconnectSettings = new AgentConfig.ReconnectSettings();
        reconnectSettings.setEnabled(true);
        reconnectSettings.setMaxAttempts(5);
        reconnectSettings.setInitialDelayMs(100);
        reconnectSettings.setMaxDelayMs(5000);
        config.setReconnect(reconnectSettings);
    }

    @Test
    void testConfigInitialization() {
        assertNotNull(config.getServer());
        assertEquals("wss://test.example.com/agent", config.getServer().getUrl());
        assertEquals("test-token", config.getServer().getToken());

        assertNotNull(config.getModels());
        assertEquals(1, config.getModels().size());

        assertNotNull(config.getReconnect());
        assertTrue(config.getReconnect().isEnabled());
        assertEquals(5, config.getReconnect().getMaxAttempts());
    }

    @Test
    void testModelConfig() {
        AgentConfig.ModelConfig model = config.getModels().get(0);

        assertEquals("gpt-4", model.getId());
        assertEquals("openai", model.getProvider());
        assertEquals("https://api.openai.com/v1", model.getEndpoint());
        assertEquals("sk-test-key", model.getApiKey());
    }

    @Test
    void testReconnectConfig() {
        AgentConfig.ReconnectSettings reconnect = config.getReconnect();

        assertTrue(reconnect.isEnabled());
        assertEquals(5, reconnect.getMaxAttempts());
        assertEquals(100, reconnect.getInitialDelayMs());
        assertEquals(5000, reconnect.getMaxDelayMs());
    }

    @Test
    void testMultipleModels() {
        AgentConfig.ModelConfig model2 = new AgentConfig.ModelConfig();
        model2.setId("claude-3-opus");
        model2.setProvider("anthropic");
        model2.setEndpoint("https://api.anthropic.com");
        model2.setApiKey("sk-ant-key");
        model2.setApiVersion("2023-06-01");

        config.setModels(List.of(config.getModels().get(0), model2));

        assertEquals(2, config.getModels().size());
        assertEquals("claude-3-opus", config.getModels().get(1).getId());
        assertEquals("2023-06-01", config.getModels().get(1).getApiVersion());
    }

    @Test
    void testMessageTypes() {
        assertEquals("register", Message.TYPE_REGISTER);
        assertEquals("register_ack", Message.TYPE_REGISTER_ACK);
        assertEquals("request", Message.TYPE_REQUEST);
        assertEquals("response", Message.TYPE_RESPONSE);
        assertEquals("error", Message.TYPE_ERROR);
        assertEquals("ping", Message.TYPE_PING);
        assertEquals("pong", Message.TYPE_PONG);
    }

    @Test
    void testRegisterMessageCreation() throws Exception {
        Message registerMsg = new Message();
        registerMsg.setType(Message.TYPE_REGISTER);
        registerMsg.setToken("test-token");

        Message.ModelInfo modelInfo = new Message.ModelInfo();
        modelInfo.setId("gpt-4");
        modelInfo.setProvider("openai");
        modelInfo.setEndpoint("https://api.openai.com/v1");
        modelInfo.setApiKey("sk-test-key");
        registerMsg.setModels(List.of(modelInfo));

        String json = objectMapper.writeValueAsString(registerMsg);

        assertTrue(json.contains("\"type\":\"register\""));
        assertTrue(json.contains("\"token\":\"test-token\""));
        assertTrue(json.contains("\"id\":\"gpt-4\""));
    }

    @Test
    void testRequestMessageCreation() throws Exception {
        Message requestMsg = new Message();
        requestMsg.setType(Message.TYPE_REQUEST);
        requestMsg.setRequestId("req-123");
        requestMsg.setModel("gpt-4");
        requestMsg.setEndpoint("/v1/chat/completions");

        String json = objectMapper.writeValueAsString(requestMsg);

        assertTrue(json.contains("\"type\":\"request\""));
        assertTrue(json.contains("\"requestId\":\"req-123\""));
        assertTrue(json.contains("\"model\":\"gpt-4\""));
    }

    @Test
    void testResponseMessageCreation() throws Exception {
        Message responseMsg = new Message();
        responseMsg.setType(Message.TYPE_RESPONSE);
        responseMsg.setRequestId("req-123");
        responseMsg.setStatus(200);

        String json = objectMapper.writeValueAsString(responseMsg);

        assertTrue(json.contains("\"type\":\"response\""));
        assertTrue(json.contains("\"requestId\":\"req-123\""));
        assertTrue(json.contains("\"status\":200"));
    }

    @Test
    void testErrorMessageCreation() throws Exception {
        Message errorMsg = new Message();
        errorMsg.setType(Message.TYPE_ERROR);
        errorMsg.setError("Invalid API key");
        errorMsg.setRequestId("req-123");

        String json = objectMapper.writeValueAsString(errorMsg);

        assertTrue(json.contains("\"type\":\"error\""));
        assertTrue(json.contains("\"error\":\"Invalid API key\""));
    }

    @Test
    void testPingPongMessage() throws Exception {
        Message ping = new Message();
        ping.setType(Message.TYPE_PING);

        String json = objectMapper.writeValueAsString(ping);
        Message parsed = objectMapper.readValue(json, Message.class);

        assertEquals(Message.TYPE_PING, parsed.getType());

        Message pong = new Message();
        pong.setType(Message.TYPE_PONG);

        String pongJson = objectMapper.writeValueAsString(pong);
        Message parsedPong = objectMapper.readValue(pongJson, Message.class);

        assertEquals(Message.TYPE_PONG, parsedPong.getType());
    }

    @Test
    void testModelInfoSerialization() throws Exception {
        Message.ModelInfo modelInfo = new Message.ModelInfo();
        modelInfo.setId("gpt-4");
        modelInfo.setProvider("openai");
        modelInfo.setEndpoint("https://api.openai.com/v1");
        modelInfo.setApiKey("sk-test");
        modelInfo.setApiVersion("2024-01-01");

        String json = objectMapper.writeValueAsString(modelInfo);
        Message.ModelInfo parsed = objectMapper.readValue(json, Message.ModelInfo.class);

        assertEquals("gpt-4", parsed.getId());
        assertEquals("openai", parsed.getProvider());
        assertEquals("sk-test", parsed.getApiKey());
        assertEquals("2024-01-01", parsed.getApiVersion());
    }
}