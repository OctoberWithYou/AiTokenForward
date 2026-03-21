package org.ljc.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.ljc.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ModelCaller {

    private static final Logger logger = LoggerFactory.getLogger(ModelCaller.class);

    private final Map<String, AgentConfig.ModelConfig> modelConfigs = new HashMap<>();
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public ModelCaller(List<AgentConfig.ModelConfig> models) {
        if (models != null) {
            for (AgentConfig.ModelConfig model : models) {
                modelConfigs.put(model.getId(), model);
            }
        }

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public Object call(String modelId, String endpoint, Object payload) throws IOException {
        AgentConfig.ModelConfig modelConfig = modelConfigs.get(modelId);
        if (modelConfig == null) {
            // 尝试使用默认模型
            modelConfig = modelConfigs.values().stream().findFirst().orElse(null);
            if (modelConfig == null) {
                throw new IOException("No model configuration found for: " + modelId);
            }
        }

        String provider = modelConfig.getProvider();
        String targetUrl = buildTargetUrl(modelConfig, endpoint);

        logger.info("Calling model: {} provider: {} url: {}", modelId, provider, targetUrl);

        Request request = buildRequest(provider, targetUrl, modelConfig, payload);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("API request failed: " + response.code() + " " + errorBody);
            }

            String responseBody = response.body().string();
            return jsonMapper.readValue(responseBody, Object.class);
        }
    }

    private String buildTargetUrl(AgentConfig.ModelConfig config, String endpoint) {
        String baseUrl = config.getEndpoint();
        if (baseUrl == null || baseUrl.isEmpty()) {
            // 默认使用 OpenAI
            baseUrl = "https://api.openai.com";
        }

        // 移除末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 确保 endpoint 以斜杠开头
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        return baseUrl + endpoint;
    }

    private Request buildRequest(String provider, String url, AgentConfig.ModelConfig config, Object payload) throws IOException {
        RequestBody body = RequestBody.create(
                jsonMapper.writeValueAsString(payload),
                MediaType.parse("application/json")
        );

        Headers.Builder headersBuilder = new Headers.Builder()
                .add("Content-Type", "application/json");

        // 根据不同的 provider 添加特定的 header
        String providerLower = provider != null ? provider.toLowerCase() : "openai";

        switch (providerLower) {
            case "openai":
                headersBuilder.add("Authorization", "Bearer " + config.getApiKey());
                break;

            case "anthropic":
                headersBuilder.add("x-api-key", config.getApiKey());
                if (config.getApiVersion() != null) {
                    headersBuilder.add("anthropic-version", config.getApiVersion());
                }
                break;

            case "azure":
                headersBuilder.add("api-key", config.getApiKey());
                if (config.getApiVersion() != null) {
                    headersBuilder.add("api-version", config.getApiVersion());
                }
                break;

            default:
                headersBuilder.add("Authorization", "Bearer " + config.getApiKey());
        }

        return new Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(body)
                .build();
    }
}