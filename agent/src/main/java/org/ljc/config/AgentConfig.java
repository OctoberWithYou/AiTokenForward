package org.ljc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AgentConfig {

    @JsonProperty("server")
    private ServerSettings server;

    @JsonProperty("models")
    private List<ModelConfig> models;

    @JsonProperty("reconnect")
    private ReconnectSettings reconnect;

    public ServerSettings getServer() {
        return server;
    }

    public void setServer(ServerSettings server) {
        this.server = server;
    }

    public List<ModelConfig> getModels() {
        return models;
    }

    public void setModels(List<ModelConfig> models) {
        this.models = models;
    }

    public ReconnectSettings getReconnect() {
        return reconnect;
    }

    public void setReconnect(ReconnectSettings reconnect) {
        this.reconnect = reconnect;
    }

    public static class ServerSettings {
        @JsonProperty("url")
        private String url;

        @JsonProperty("token")
        private String token;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class ModelConfig {
        @JsonProperty("id")
        private String id;

        @JsonProperty("provider")
        private String provider;

        @JsonProperty("endpoint")
        private String endpoint;

        @JsonProperty("apiKey")
        private String apiKey;

        @JsonProperty("apiVersion")
        private String apiVersion;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }
    }

    public static class ReconnectSettings {
        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("maxAttempts")
        private int maxAttempts;

        @JsonProperty("initialDelayMs")
        private long initialDelayMs;

        @JsonProperty("maxDelayMs")
        private long maxDelayMs;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }
    }
}