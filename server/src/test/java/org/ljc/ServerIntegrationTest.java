package org.ljc;

import org.junit.jupiter.api.Test;
import org.ljc.config.ServerConfig;
import org.ljc.common.Message;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server 集成测试
 */
class ServerIntegrationTest {

    /**
     * 测试服务器配置加载
     */
    @Test
    void testServerConfigLoading() throws Exception {
        String yaml = """
            server:
              host: "0.0.0.0"
              port: 8080
              ssl:
                enabled: false
            externalClient:
              tokenFile: "config/token.txt"
              headerName: "X-Auth-Token"
            auth:
              apiKeys:
                - "sk-key-001"
                - "sk-key-002"
            agent:
              connection:
                token: "agent-secret-token"
                maxAgents: 5
            """;

        ServerConfig config = ConfigLoader.fromYaml(yaml, ServerConfig.class);

        assertNotNull(config);
        assertNotNull(config.getServer());
        assertEquals("0.0.0.0", config.getServer().getHost());
        assertEquals(8080, config.getServer().getPort());
        assertFalse(config.getServer().getSsl().isEnabled());

        assertNotNull(config.getExternalClient());
        assertEquals("config/token.txt", config.getExternalClient().getTokenFile());
        assertEquals("X-Auth-Token", config.getExternalClient().getHeaderName());

        assertNotNull(config.getAuth());
        assertEquals(2, config.getAuth().getApiKeys().size());

        assertNotNull(config.getAgent());
        assertNotNull(config.getAgent().getConnection());
        assertEquals("agent-secret-token", config.getAgent().getConnection().getToken());
        assertEquals(5, config.getAgent().getConnection().getMaxAgents());
    }

    /**
     * 测试消息序列化/反序列化
     */
    @Test
    void testMessageSerialization() throws Exception {
        // 测试注册消息
        Message registerMsg = new Message();
        registerMsg.setType(Message.TYPE_REGISTER);
        registerMsg.setToken("agent-secret-token");

        Message.ModelInfo modelInfo = new Message.ModelInfo();
        modelInfo.setId("gpt-4");
        modelInfo.setProvider("openai");
        registerMsg.setModels(List.of(modelInfo));

        String json = ConfigLoader.toJson(registerMsg);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"register\""));

        Message deserialized = ConfigLoader.fromJson(json, Message.class);
        assertEquals(Message.TYPE_REGISTER, deserialized.getType());
    }

    /**
     * 测试转发请求消息
     */
    @Test
    void testForwardRequestMessage() throws Exception {
        Message requestMsg = new Message();
        requestMsg.setType(Message.TYPE_REQUEST);
        requestMsg.setRequestId("req-123");
        requestMsg.setModel("gpt-4");
        requestMsg.setEndpoint("/v1/chat/completions");
        requestMsg.setPayload(java.util.Map.of(
            "model", "gpt-4",
            "messages", List.of(java.util.Map.of("role", "user", "content", "Hi"))
        ));

        String json = ConfigLoader.toJson(requestMsg);
        Message deserialized = ConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_REQUEST, deserialized.getType());
        assertEquals("req-123", deserialized.getRequestId());
        assertEquals("gpt-4", deserialized.getModel());
    }

    /**
     * 测试转发响应消息
     */
    @Test
    void testForwardResponseMessage() throws Exception {
        Message responseMsg = new Message();
        responseMsg.setType(Message.TYPE_RESPONSE);
        responseMsg.setRequestId("req-123");
        responseMsg.setStatus(200);
        responseMsg.setBody(java.util.Map.of(
            "id", "chatcmpl-abc",
            "object", "chat.completion",
            "created", 1234567890,
            "choices", List.of()
        ));

        String json = ConfigLoader.toJson(responseMsg);
        Message deserialized = ConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_RESPONSE, deserialized.getType());
        assertEquals(200, deserialized.getStatus());
    }

    /**
     * 测试认证配置
     */
    @Test
    void testAuthConfig() throws Exception {
        String yaml = """
            auth:
              apiKeys:
                - "key1"
                - "key2"
            """;

        ServerConfig config = ConfigLoader.fromYaml(yaml, ServerConfig.class);
        assertNotNull(config.getAuth());
        assertTrue(config.getAuth().getApiKeys().contains("key1"));
        assertTrue(config.getAuth().getApiKeys().contains("key2"));
    }

    /**
     * 测试外部客户端认证配置
     */
    @Test
    void testExternalClientConfig() throws Exception {
        String yaml = """
            externalClient:
              tokenFile: "path/to/token"
              headerName: "X-Custom-Header"
            """;

        ServerConfig config = ConfigLoader.fromYaml(yaml, ServerConfig.class);
        assertNotNull(config.getExternalClient());
        assertEquals("path/to/token", config.getExternalClient().getTokenFile());
        assertEquals("X-Custom-Header", config.getExternalClient().getHeaderName());
    }
}