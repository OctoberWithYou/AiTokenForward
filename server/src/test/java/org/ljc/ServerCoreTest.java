package org.ljc;

import org.junit.jupiter.api.Test;
import org.ljc.config.ServerConfig;
import org.ljc.common.Message;
import org.ljc.server.AuthManager;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server 核心功能测试
 */
class ServerCoreTest {

    @Test
    void testServerConfigDefaults() {
        ServerConfig config = new ServerConfig();

        // 测试默认 ServerSettings
        ServerConfig.ServerSettings serverSettings = new ServerConfig.ServerSettings();
        serverSettings.setHost("0.0.0.0");
        serverSettings.setPort(8080);

        ServerConfig.SslSettings sslSettings = new ServerConfig.SslSettings();
        sslSettings.setEnabled(false);
        serverSettings.setSsl(sslSettings);

        config.setServer(serverSettings);

        assertEquals("0.0.0.0", config.getServer().getHost());
        assertEquals(8080, config.getServer().getPort());
        assertFalse(config.getServer().getSsl().isEnabled());
    }

    @Test
    void testServerConfigSsl() {
        ServerConfig config = new ServerConfig();

        ServerConfig.ServerSettings serverSettings = new ServerConfig.ServerSettings();
        serverSettings.setHost("0.0.0.0");
        serverSettings.setPort(443);

        ServerConfig.SslSettings sslSettings = new ServerConfig.SslSettings();
        sslSettings.setEnabled(true);
        sslSettings.setKeyStore("/path/to/keystore.jks");
        sslSettings.setKeyStorePassword("password");
        serverSettings.setSsl(sslSettings);

        config.setServer(serverSettings);

        assertTrue(config.getServer().getSsl().isEnabled());
        assertEquals("/path/to/keystore.jks", config.getServer().getSsl().getKeyStore());
    }

    @Test
    void testExternalClientConfig() {
        ServerConfig.ExternalClientSettings externalClient = new ServerConfig.ExternalClientSettings();
        externalClient.setTokenFile("config/token.txt");
        externalClient.setHeaderName("X-Custom-Token");

        ServerConfig config = new ServerConfig();
        config.setExternalClient(externalClient);

        assertEquals("config/token.txt", config.getExternalClient().getTokenFile());
        assertEquals("X-Custom-Token", config.getExternalClient().getHeaderName());
    }

    @Test
    void testAuthConfig() {
        ServerConfig.AuthSettings auth = new ServerConfig.AuthSettings();
        auth.setApiKeys(java.util.List.of("key1", "key2", "key3"));

        ServerConfig config = new ServerConfig();
        config.setAuth(auth);

        assertEquals(3, config.getAuth().getApiKeys().size());
        assertTrue(config.getAuth().getApiKeys().contains("key2"));
    }

    @Test
    void testAgentConnectionConfig() {
        ServerConfig.ConnectionSettings connection = new ServerConfig.ConnectionSettings();
        connection.setToken("agent-secret-token");
        connection.setMaxAgents(20);

        ServerConfig.AgentSettings agent = new ServerConfig.AgentSettings();
        agent.setConnection(connection);

        ServerConfig config = new ServerConfig();
        config.setAgent(agent);

        assertEquals("agent-secret-token", config.getAgent().getConnection().getToken());
        assertEquals(20, config.getAgent().getConnection().getMaxAgents());
    }

    @Test
    void testAuthManagerInitialization() {
        AuthManager authManager = new AuthManager();

        Set<String> apiKeys = new HashSet<>();
        apiKeys.add("test-key-1");
        apiKeys.add("test-key-2");

        authManager.init(apiKeys, null, "X-Auth-Token");

        // 测试 API Key 验证
        assertTrue(authManager.validateApiKey("test-key-1"));
        assertTrue(authManager.validateApiKey("test-key-2"));
        assertFalse(authManager.validateApiKey("invalid-key"));

        assertEquals("X-Auth-Token", authManager.getHeaderName());
    }

    @Test
    void testAuthManagerWithExternalToken() {
        AuthManager authManager = new AuthManager();

        // 注意：这里不实际加载文件，只测试方法存在
        authManager.init(new HashSet<>(), null, "X-Custom-Token");

        // 没有配置外部 token 时，应该跳过验证
        assertFalse(authManager.hasExternalToken());
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
    void testMessageModelInfo() {
        Message.ModelInfo modelInfo = new Message.ModelInfo();
        modelInfo.setId("gpt-4");
        modelInfo.setProvider("openai");
        modelInfo.setEndpoint("https://api.openai.com/v1");
        modelInfo.setApiKey("sk-test");
        modelInfo.setApiVersion("2024-01-01");

        assertEquals("gpt-4", modelInfo.getId());
        assertEquals("openai", modelInfo.getProvider());
        assertEquals("sk-test", modelInfo.getApiKey());
    }

    @Test
    void testMessageSerialization() throws Exception {
        Message msg = new Message();
        msg.setType(Message.TYPE_REQUEST);
        msg.setRequestId("req-123");
        msg.setModel("gpt-4");
        msg.setEndpoint("/v1/chat/completions");
        msg.setPayload(java.util.Map.of(
            "model", "gpt-4",
            "messages", java.util.List.of(java.util.Map.of("role", "user", "content", "Hi"))
        ));

        String json = ConfigLoader.toJson(msg);
        Message parsed = ConfigLoader.fromJson(json, Message.class);

        assertEquals(Message.TYPE_REQUEST, parsed.getType());
        assertEquals("req-123", parsed.getRequestId());
        assertEquals("gpt-4", parsed.getModel());
    }

    @Test
    void testFullServerConfig() throws Exception {
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
            agent:
              connection:
                token: "agent-token"
                maxAgents: 10
            """;

        ServerConfig config = ConfigLoader.fromYaml(yaml, ServerConfig.class);

        assertNotNull(config);
        assertEquals("0.0.0.0", config.getServer().getHost());
        assertEquals(8080, config.getServer().getPort());
        assertFalse(config.getServer().getSsl().isEnabled());

        assertEquals("config/token.txt", config.getExternalClient().getTokenFile());
        assertEquals("X-Auth-Token", config.getExternalClient().getHeaderName());

        assertEquals(1, config.getAuth().getApiKeys().size());
        assertEquals("agent-token", config.getAgent().getConnection().getToken());
        assertEquals(10, config.getAgent().getConnection().getMaxAgents());
    }
}