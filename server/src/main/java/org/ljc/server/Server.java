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
import java.nio.ByteBuffer;
import java.nio.channels.*;
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
    private static final int WEBSOCKET_BUFFER_SIZE = 16384;

    private final ServerConfig config;
    private final AgentManager agentManager;
    private final AgentWebSocketHandler agentHandler;
    private final AuthManager authManager;
    private final ClientHttpHandler clientHandler;
    private HttpServer httpServer;

    // NIO WebSocket server
    private ServerSocketChannel wsServerChannel;
    private Selector wsSelector;
    private Thread wsServerThread;
    private volatile boolean running = true;

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

        // 设置响应回调 - 将 Agent 的响应路由到 ClientHttpHandler
        agentHandler.setResponseCallback((requestId, response) -> {
            clientHandler.onAgentResponse(requestId, response);
        });
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

        // 启动 NIO WebSocket 服务器
        startWebSocketServer(host, port + 1);

        logger.info("Server started on {}:{} (HTTP) and {}:{} (WebSocket)", host, port, host, port + 1);
    }

    private void startHttpServer(String host, int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());

        // 添加 HTTP 处理器
        httpServer.createContext("/", clientHandler);

        // WebSocket 升级处理器 - 重定向到 NIO WebSocket 服务器
        httpServer.createContext("/agent", exchange -> {
            // 返回 400，要求客户端连接到 WebSocket 端口
            exchange.getResponseHeaders().set("X-WebSocket-Port", String.valueOf(port + 1));
            exchange.sendResponseHeaders(400, -1);
        });

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
        httpsServer.createContext("/agent", exchange -> {
            exchange.getResponseHeaders().set("X-WebSocket-Port", String.valueOf(port + 1));
            exchange.sendResponseHeaders(400, -1);
        });

        httpsServer.start();

        this.httpServer = httpsServer;
        logger.info("HTTPS Server started on {}:{}", host, port);
    }

    private void startWebSocketServer(String host, int port) throws IOException {
        try {
            wsSelector = Selector.open();
            wsServerChannel = ServerSocketChannel.open();
            wsServerChannel.bind(new InetSocketAddress(host, port));
            wsServerChannel.configureBlocking(false);
            wsServerChannel.register(wsSelector, SelectionKey.OP_ACCEPT);

            wsServerThread = new Thread(this::runWebSocketServer, "WebSocket-Server");
            wsServerThread.start();

            // Give the thread time to start
            Thread.sleep(100);

            logger.info("WebSocket server started on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start WebSocket server: {}", e.getMessage(), e);
            throw new IOException("WebSocket server failed to start", e);
        }
    }

    private void runWebSocketServer() {
        while (running) {
            try {
                wsSelector.select(100);

                Set<SelectionKey> selectedKeys = wsSelector.selectedKeys();
                for (SelectionKey key : selectedKeys) {
                    try {
                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            readFrame(key);
                        } else if (key.isWritable()) {
                            // Handle writeable state if needed
                        }
                    } catch (Exception e) {
                        logger.error("Error processing WebSocket key: {}", e.getMessage());
                        closeConnection(key);
                    }
                }
                selectedKeys.clear();
            } catch (IOException e) {
                if (running) {
                    logger.error("WebSocket server error: {}", e.getMessage());
                }
            }
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client != null) {
            client.configureBlocking(false);
            client.register(wsSelector, SelectionKey.OP_READ);
            logger.debug("New WebSocket connection from {}", client.getRemoteAddress());
        }
    }

    private void readFrame(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(WEBSOCKET_BUFFER_SIZE);

        int bytesRead = client.read(buffer);
        if (bytesRead == -1) {
            // Client closed connection
            closeConnection(key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            logger.debug("Received {} bytes from {}", bytesRead, client.getRemoteAddress());

            // 检查是否是 HTTP 握手请求
            String request = new String(data, StandardCharsets.UTF_8);
            if (request.startsWith("GET")) {
                handleHttpHandshake(client, request);
            } else {
                // 解析 WebSocket 帧
                String text = parseWebSocketFrame(data, client);
                if (text != null && !text.isEmpty()) {
                    logger.debug("Parsed WebSocket message: {}", text.substring(0, Math.min(100, text.length())));
                    handleWebSocketMessage(key, client, text);
                }
            }
        }
    }

    private void handleHttpHandshake(SocketChannel client, String request) throws IOException {
        // 解析 WebSocket 密钥
        String key = null;
        for (String line : request.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(":") + 1).trim();
                break;
            }
        }

        if (key == null) {
            client.close();
            return;
        }

        // 生成响应
        String acceptKey = generateAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n";

        ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        while (responseBuffer.hasRemaining()) {
            client.write(responseBuffer);
        }

        // 创建会话
        String sessionId = UUID.randomUUID().toString();
        WebSocketSessionImpl session = new WebSocketSessionImpl(sessionId, client, key);
        webSocketSessions.put(sessionId, session);

        logger.info("WebSocket connection established: {}", sessionId);

        // 通知 handler
        client.register(wsSelector, SelectionKey.OP_READ);
        agentHandler.handleOpen(sessionId, session);
    }

    private static final int OP_TEXT = 0x1;
    private static final int OP_BINARY = 0x2;
    private static final int OP_CLOSE = 0x8;
    private static final int OP_PING = 0x9;
    private static final int OP_PONG = 0xA;

    // Parse WebSocket frame and return text, or null for close/control frames
    // Returns "" for ping frames (caller should respond with pong)
    // Returns null for close frames
    private String parseWebSocketFrame(byte[] data, SocketChannel client) throws IOException {
        if (data.length < 2) return null;

        int firstByte = data[0] & 0xFF;
        int secondByte = data[1] & 0xFF;

        int opcode = firstByte & 0x0F;
        // 0x8 = close frame
        if (opcode == OP_CLOSE) {
            return null;
        }

        // 0x9 = ping frame - respond with pong
        if (opcode == OP_PING) {
            sendPongFrame(client);
            return ""; // Return empty to indicate ping was handled
        }

        // 0xA = pong frame - ignore
        if (opcode == OP_PONG) {
            return "";
        }

        // Only handle text frames for now
        if (opcode != OP_TEXT && opcode != OP_BINARY) {
            return "";
        }

        boolean masked = (secondByte & 0x80) != 0;
        long payloadLength = secondByte & 0x7F;

        int offset = 2;
        if (payloadLength == 126) {
            if (data.length < 4) return null;
            payloadLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            offset = 4;
        } else if (payloadLength == 127) {
            if (data.length < 10) return null;
            payloadLength = 0;
            for (int i = 0; i < 8; i++) {
                payloadLength = (payloadLength << 8) | (data[2 + i] & 0xFF);
            }
            offset = 10;
        }

        byte[] mask = null;
        if (masked) {
            if (data.length < offset + 4) return null;
            mask = new byte[4];
            System.arraycopy(data, offset, mask, 0, 4);
            offset += 4;
        }

        if (data.length < offset + payloadLength) return null;

        byte[] payload = new byte[(int) payloadLength];
        System.arraycopy(data, offset, payload, 0, (int) payloadLength);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private void sendPongFrame(SocketChannel client) throws IOException {
        // Pong frame: opcode 0xA, no payload
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{(byte)0x8A, 0x00});
        while (buffer.hasRemaining()) {
            client.write(buffer);
        }
    }

    private void handleWebSocketMessage(SelectionKey key, SocketChannel client, String text) {
        // 找到对应的会话
        String sessionId = null;
        for (var entry : webSocketSessions.entrySet()) {
            if (entry.getValue().channel == client) {
                sessionId = entry.getKey();
                break;
            }
        }

        logger.debug("Handling WebSocket message for session: {}", sessionId);

        if (sessionId != null) {
            WebSocketSessionImpl session = webSocketSessions.get(sessionId);
            Message response = agentHandler.handleMessage(sessionId, text, session);
            if (response != null) {
                try {
                    String json = ConfigLoader.toJson(response);
                    sendWebSocketFrame(client, json);
                } catch (IOException e) {
                    logger.error("Failed to send response: {}", e.getMessage());
                }
            }
        }
    }

    private void sendWebSocketFrame(SocketChannel client, String text) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int length = textBytes.length;

        ByteBuffer buffer;
        if (length <= 125) {
            buffer = ByteBuffer.allocate(2 + length);
            buffer.put((byte) 0x81); // Text frame
            buffer.put((byte) length);
        } else if (length <= 65535) {
            buffer = ByteBuffer.allocate(4 + length);
            buffer.put((byte) 0x81); // Text frame
            buffer.put((byte) 126);
            buffer.put((byte) (length >> 8));
            buffer.put((byte) (length & 0xFF));
        } else {
            buffer = ByteBuffer.allocate(10 + length);
            buffer.put((byte) 0x81); // Text frame
            buffer.put((byte) 127);
            for (int i = 7; i >= 0; i--) {
                buffer.put((byte) ((length >> (i * 8)) & 0xFF));
            }
        }

        buffer.put(textBytes);
        buffer.flip();

        while (buffer.hasRemaining()) {
            client.write(buffer);
        }
    }

    private void closeConnection(SelectionKey key) {
        try {
            String sessionId = null;
            for (var entry : webSocketSessions.entrySet()) {
                if (entry.getValue().channel == key.channel()) {
                    sessionId = entry.getKey();
                    break;
                }
            }

            if (sessionId != null) {
                webSocketSessions.remove(sessionId);
                agentHandler.handleClose(sessionId);
            }

            key.cancel();
            key.channel().close();
        } catch (IOException e) {
            logger.debug("Error closing connection: {}", e.getMessage());
        }
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
        running = false;

        // 关闭 WebSocket 服务器
        if (wsSelector != null) {
            wsSelector.wakeup();
        }
        if (wsServerThread != null) {
            wsServerThread.interrupt();
        }
        try {
            if (wsServerChannel != null) {
                wsServerChannel.close();
            }
            if (wsSelector != null) {
                wsSelector.close();
            }
        } catch (IOException e) {
            logger.debug("Error closing WebSocket server: {}", e.getMessage());
        }

        // 关闭 HTTP 服务器
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("Server stopped");
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
        final SocketChannel channel;
        private volatile boolean open = true;
        private final String key;

        public WebSocketSessionImpl(String sessionId, SocketChannel channel, String key) {
            this.sessionId = sessionId;
            this.channel = channel;
            this.key = key;
        }

        public String getSessionId() {
            return sessionId;
        }

        @Override
        public void sendText(String text) throws IOException {
            if (!open) {
                throw new IOException("Session is closed");
            }
            sendWebSocketFrame(channel, text);
            logger.debug("Sent WebSocket message: {}", text.substring(0, Math.min(100, text.length())));
        }

        public void close() {
            open = false;
            webSocketSessions.remove(sessionId);
            agentHandler.handleClose(sessionId);
            try {
                channel.close();
            } catch (IOException e) {
                logger.debug("Error closing channel: {}", e.getMessage());
            }
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