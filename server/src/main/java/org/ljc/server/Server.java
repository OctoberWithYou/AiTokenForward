package org.ljc.server;

import com.sun.net.httpserver.*;
import org.ljc.ConfigLoader;
import org.ljc.common.Message;
import org.ljc.config.ServerConfig;
import org.ljc.server.AgentManager;
import org.ljc.server.AgentWebSocketHandler;
import org.ljc.server.AuthManager;
import org.ljc.server.ClientHttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerConfig config;
    private final AgentManager agentManager;
    private final AgentWebSocketHandler agentHandler;
    private final AuthManager authManager;
    private final ClientHttpHandler clientHandler;
    private HttpServer httpServer;

    // 活跃的 WebSocket 会话
    private final ConcurrentHashMap<String, WebSocketSessionImpl> webSocketSessions = new ConcurrentHashMap<>();

    public Server(ServerConfig config) {
        this.config = config;

        // 初始化组件
        ServerConfig.AgentSettings agentSettings = config.getAgent();
        String agentToken = agentSettings != null && agentSettings.getConnection() != null
                ? agentSettings.getConnection().getToken() : "default-token";
        int maxAgents = agentSettings != null && agentSettings.getConnection() != null
                ? agentSettings.getConnection().getMaxAgents() : 10;

        this.agentManager = new AgentManager(agentToken, maxAgents);
        this.agentHandler = new AgentWebSocketHandler(agentManager, agentToken);

        ServerConfig.ExternalClientSettings externalSettings = config.getExternalClient();
        String tokenFile = externalSettings != null ? externalSettings.getTokenFile() : null;
        String headerName = externalSettings != null ? externalSettings.getHeaderName() : null;

        ServerConfig.AuthSettings authSettings = config.getAuth();
        Set<String> apiKeys = authSettings != null && authSettings.getApiKeys() != null
                ? new HashSet<>(authSettings.getApiKeys()) : new HashSet<>();

        this.authManager = new AuthManager();
        this.authManager.init(apiKeys, tokenFile, headerName);

        this.clientHandler = new ClientHttpHandler(authManager, agentManager, agentHandler);
    }

    public void start() throws IOException {
        ServerConfig.ServerSettings serverSettings = config.getServer();
        String host = serverSettings != null && serverSettings.getHost() != null
                ? serverSettings.getHost() : "0.0.0.0";
        int port = serverSettings != null && serverSettings.getPort() > 0
                ? serverSettings.getPort() : 8080;

        if (serverSettings != null && serverSettings.getSsl() != null && serverSettings.getSsl().isEnabled()) {
            // HTTPS 服务器
            startHttpsServer(host, port, serverSettings.getSsl());
        } else {
            // HTTP 服务器
            startHttpServer(host, port);
        }

        logger.info("Server started on {}:{}", host, port);
    }

    private void startHttpServer(String host, int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());

        // 添加 HTTP 处理器
        httpServer.createContext("/", clientHandler);

        // 添加 WebSocket 升级处理器
        httpServer.createContext("/agent", new AgentWebSocketHttpHandler());

        httpServer.start();
    }

    private void startHttpsServer(String host, int port, ServerConfig.SslSettings sslSettings) throws IOException {
        SSLContext sslContext = createSslContext(sslSettings);

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(host, port), 0);
        httpsServer.setExecutor(Executors.newCachedThreadPool());
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    SSLContext context = getSSLContext();
                    SSLParameters sslParams = context.getSupportedSSLParameters();
                    params.setSSLParameters(sslParams);
                } catch (Exception e) {
                    logger.error("Failed to configure SSL: {}", e.getMessage());
                }
            }
        });

        httpsServer.createContext("/", clientHandler);
        httpsServer.createContext("/agent", new AgentWebSocketHttpHandler());

        httpsServer.start();

        this.httpServer = httpsServer;
        logger.info("HTTPS Server started on {}:{}", host, port);
    }

    private SSLContext createSslContext(ServerConfig.SslSettings sslSettings) throws IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(sslSettings.getKeyStore()), sslSettings.getKeyStorePassword().toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, sslSettings.getKeyStorePassword().toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            return sslContext;
        } catch (Exception e) {
            throw new IOException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("Server stopped");
        }
    }

    // WebSocket HTTP 处理器 - 处理握手
    private class AgentWebSocketHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            if (upgrade == null || !upgrade.equalsIgnoreCase("websocket")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String key = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
            if (key == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // 生成 accept 密钥
            String acceptKey = generateAcceptKey(key);

            // 发送 101 切换协议响应
            exchange.getResponseHeaders().set("Upgrade", "websocket");
            exchange.getResponseHeaders().set("Connection", "Upgrade");
            exchange.getResponseHeaders().set("Sec-WebSocket-Accept", acceptKey);
            exchange.getResponseHeaders().set("Sec-WebSocket-Version", "13");
            exchange.sendResponseHeaders(101, -1);

            // 创建 WebSocket 会话
            String sessionId = UUID.randomUUID().toString();
            WebSocketSessionImpl session = new WebSocketSessionImpl(sessionId);
            webSocketSessions.put(sessionId, session);

            logger.info("WebSocket connection established: {}", sessionId);

            // 通知 handler 有新连接
            agentHandler.handleOpen(sessionId, session);
        }
    }

    private String generateAcceptKey(String key) {
        try {
            String combined = key + WEBSOCKET_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(combined.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate accept key", e);
        }
    }

    // WebSocket 会话实现
    private class WebSocketSessionImpl implements Message.WebSocketSession {
        private final String sessionId;
        private volatile boolean open = true;

        public WebSocketSessionImpl(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        @Override
        public void sendText(String text) throws IOException {
            if (!open) {
                throw new IOException("Session is closed");
            }
            // 这里需要实现完整的 WebSocket 帧发送
            // 简化实现：实际应该使用 javax.websocket 或其他 WebSocket 库
            logger.debug("Sending WebSocket message: {}", text.substring(0, Math.min(100, text.length())));
        }

        public void close() {
            open = false;
            webSocketSessions.remove(sessionId);
            agentHandler.handleClose(sessionId);
        }

        public boolean isOpen() {
            return open;
        }
    }

    public static void main(String[] args) {
        String configPath = "config/server-config.yaml";

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
                i++;
            }
        }

        try {
            ServerConfig config = ConfigLoader.loadServerConfig(configPath);
            Server server = new Server(config);
            server.start();

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        } catch (Exception e) {
            logger.error("Failed to start server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}