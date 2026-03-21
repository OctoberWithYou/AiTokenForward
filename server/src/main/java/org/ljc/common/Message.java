package org.ljc.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    @JsonProperty("type")
    private String type;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("token")
    private String token;

    @JsonProperty("models")
    private List<ModelInfo> models;

    @JsonProperty("model")
    private String model;

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("payload")
    private Object payload;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("body")
    private Object body;

    @JsonProperty("error")
    private String error;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<ModelInfo> getModels() {
        return models;
    }

    public void setModels(List<ModelInfo> models) {
        this.models = models;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    // Message types
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_REGISTER_ACK = "register_ack";
    public static final String TYPE_REQUEST = "request";
    public static final String TYPE_RESPONSE = "response";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelInfo {
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

    // WebSocket Session interface for server side
    public interface WebSocketSession {
        void sendText(String text) throws IOException;
    }
}