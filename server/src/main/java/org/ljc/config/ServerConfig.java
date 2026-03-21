package org.ljc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ServerConfig {

    @JsonProperty("server")
    private ServerSettings server;

    @JsonProperty("auth")
    private AuthSettings auth;

    @JsonProperty("externalClient")
    private ExternalClientSettings externalClient;

    @JsonProperty("agent")
    private AgentSettings agent;

    public ServerSettings getServer() {
        return server;
    }

    public void setServer(ServerSettings server) {
        this.server = server;
    }

    public AuthSettings getAuth() {
        return auth;
    }

    public void setAuth(AuthSettings auth) {
        this.auth = auth;
    }

    public ExternalClientSettings getExternalClient() {
        return externalClient;
    }

    public void setExternalClient(ExternalClientSettings externalClient) {
        this.externalClient = externalClient;
    }

    public AgentSettings getAgent() {
        return agent;
    }

    public void setAgent(AgentSettings agent) {
        this.agent = agent;
    }

    public static class ServerSettings {
        @JsonProperty("host")
        private String host;

        @JsonProperty("port")
        private int port;

        @JsonProperty("ssl")
        private SslSettings ssl;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public SslSettings getSsl() {
            return ssl;
        }

        public void setSsl(SslSettings ssl) {
            this.ssl = ssl;
        }
    }

    public static class SslSettings {
        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("keyStore")
        private String keyStore;

        @JsonProperty("keyStorePassword")
        private String keyStorePassword;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyStore() {
            return keyStore;
        }

        public void setKeyStore(String keyStore) {
            this.keyStore = keyStore;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }
    }

    public static class AuthSettings {
        @JsonProperty("apiKeys")
        private List<String> apiKeys;

        public List<String> getApiKeys() {
            return apiKeys;
        }

        public void setApiKeys(List<String> apiKeys) {
            this.apiKeys = apiKeys;
        }
    }

    public static class AgentSettings {
        @JsonProperty("connection")
        private ConnectionSettings connection;

        public ConnectionSettings getConnection() {
            return connection;
        }

        public void setConnection(ConnectionSettings connection) {
            this.connection = connection;
        }
    }

    public static class ConnectionSettings {
        @JsonProperty("token")
        private String token;

        @JsonProperty("maxAgents")
        private int maxAgents;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public int getMaxAgents() {
            return maxAgents;
        }

        public void setMaxAgents(int maxAgents) {
            this.maxAgents = maxAgents;
        }
    }

    // 外部客户端认证设置
    public static class ExternalClientSettings {
        @JsonProperty("tokenFile")
        private String tokenFile;

        @JsonProperty("headerName")
        private String headerName;

        public String getTokenFile() {
            return tokenFile;
        }

        public void setTokenFile(String tokenFile) {
            this.tokenFile = tokenFile;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }
}