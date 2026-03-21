package org.ljc;

import org.junit.jupiter.api.Test;
import org.ljc.config.AgentConfig;
import org.ljc.common.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 集成测试
 */
class AgentIntegrationTest {

    /**
     * 测试配置加载
     */
    @Test
    void testAgentConfigLoading() throws Exception {
        // 测试配置解析
        String yaml = """
            server:
              url: "wss://test.example.com/agent"
              token: "test-token"
            models:
              - id: "gpt-4"
                provider: "openai"
                endpoint: "https://api.openai.com/v1"
                apiKey: "sk-test-key"
            reconnect:
              enabled: true
              maxAttempts: 5
              initialDelayMs: 1000
              maxDelayMs: 30000
            """;

        AgentConfig config = AgentConfigLoader.fromYaml(yaml, AgentConfig.class);

        assertNotNull(config);
        assertNotNull(config.getServer());
        assertEquals("wss://test.example.com/agent", config.getServer().getUrl());
        assertEquals("test-token", config.getServer().getToken());

        assertNotNull(config.getModels());
        assertEquals(1, config.getModels().size());
        assertEquals("gpt-4", config.getModels().get(0).getId());
        assertEquals("openai", config.getModels().get(0).getProvider());

        assertNotNull(config.getReconnect());
        assertTrue(config.getReconnect().isEnabled());
    }

    /**
     * 测试消息序列化/反序列化
     */
    @Test
    void testMessageSerialization() throws Exception {
        // 测试注册消息
        Message registerMsg = new Message();
        registerMsg.setType(Message.TYPE_REGISTER);
        registerMsg.setToken("test-token");

        Message.ModelInfo modelInfo = new Message.ModelInfo();
        modelInfo.setId("gpt-4");
        modelInfo.setProvider("openai");
        modelInfo.setEndpoint("https://api.openai.com/v1");
        modelInfo.setApiKey("sk-test-key");
        registerMsg.setModels(List.of(modelInfo));

        String json = AgentConfigLoader.toJson(registerMsg);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"register\""));
        assertTrue(json.contains("\"token\":\"test-token\""));

        // 反序列化
        Message deserialized = AgentConfigLoader.fromJson(json, Message.class);
        assertEquals(Message.TYPE_REGISTER, deserialized.getType());
        assertEquals("test-token", deserialized.getToken());
        assertNotNull(deserialized.getModels());
        assertEquals(1, deserialized.getModels().size());
        assertEquals("gpt-4", deserialized.getModels().get(0).getId());
    }

    /**
     * 测试请求消息
     */
    @Test
    void testRequestMessage() throws Exception {
        Message requestMsg = new Message();
        requestMsg.setType(Message.TYPE_REQUEST);
        requestMsg.setRequestId("test-request-id");
        requestMsg.setModel("gpt-4");
        requestMsg.setEndpoint("/v1/chat/completions");
        requestMsg.setPayload(java.util.Map.of(
            "model", "gpt-4",
            "messages", List.of(java.util.Map.of("role", "user", "content", "Hello"))
        ));

        String json = AgentConfigLoader.toJson(requestMsg);
        Message deserialized = AgentConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_REQUEST, deserialized.getType());
        assertEquals("test-request-id", deserialized.getRequestId());
        assertEquals("gpt-4", deserialized.getModel());
        assertEquals("/v1/chat/completions", deserialized.getEndpoint());
        assertNotNull(deserialized.getPayload());
    }

    /**
     * 测试响应消息
     */
    @Test
    void testResponseMessage() throws Exception {
        Message responseMsg = new Message();
        responseMsg.setType(Message.TYPE_RESPONSE);
        responseMsg.setRequestId("test-request-id");
        responseMsg.setStatus(200);
        responseMsg.setBody(java.util.Map.of(
            "id", "chatcmpl-123",
            "object", "chat.completion",
            "choices", List.of()
        ));

        String json = AgentConfigLoader.toJson(responseMsg);
        Message deserialized = AgentConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_RESPONSE, deserialized.getType());
        assertEquals("test-request-id", deserialized.getRequestId());
        assertEquals(200, deserialized.getStatus());
        assertNotNull(deserialized.getBody());
    }

    /**
     * 测试错误消息
     */
    @Test
    void testErrorMessage() throws Exception {
        Message errorMsg = new Message();
        errorMsg.setType(Message.TYPE_ERROR);
        errorMsg.setError("Invalid API key");

        String json = AgentConfigLoader.toJson(errorMsg);
        Message deserialized = AgentConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_ERROR, deserialized.getType());
        assertEquals("Invalid API key", deserialized.getError());
    }
}