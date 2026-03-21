package org.ljc;

import com.sun.net.httpserver.*;
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
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final ServerConfig config;
    private final AgentManager agentManager;
    private final AgentWebSocketHandler agentHandler;
    private final AuthManager authManager;
    private final ClientHttpHandler clientHandler;
    private HttpServer httpServer;

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

        // 添加 HTTP 处理器
        httpServer.createContext("/", clientHandler);

        // 添加 WebSocket 升级处理器
        httpServer.createContext("/agent", new AgentHttpHandler(agentHandler));

        httpServer.setExecutor(null);
        httpServer.start();
    }

    private void startHttpsServer(String host, int port, ServerConfig.SslSettings sslSettings) throws IOException {
        // 创建 SSL 上下文
        SSLContext sslContext = createSslContext(sslSettings);

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(host, port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    SSLContext context = getSSLContext();
                    SSLEngine engine = context.createSSLEngine();
                    SSLParameters sslParams = context.getSupportedSSLParameters();
                    params.setSSLParameters(sslParams);
                } catch (Exception e) {
                    logger.error("Failed to configure SSL: {}", e.getMessage());
                }
            }
        });

        httpsServer.createContext("/", clientHandler);
        httpsServer.createContext("/agent", new AgentHttpHandler(agentHandler));

        httpsServer.setExecutor(null);
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

    // HTTP 处理器，用于处理 WebSocket 升级
    private static class AgentHttpHandler implements HttpHandler {
        private final AgentWebSocketHandler agentHandler;

        public AgentHttpHandler(AgentWebSocketHandler agentHandler) {
            this.agentHandler = agentHandler;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 这是一个简化的实现
            // 实际需要实现完整的 WebSocket 握手和处理
            logger.debug("Agent WebSocket connection request");

            // 返回 400 表示需要 WebSocket 升级
            exchange.sendResponseHeaders(400, -1);
        }
    }
}