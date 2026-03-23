package org.ljc.integration;

import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the AI Model Proxy Forward system
 * Tests the server and agent as black-box using built JARs
 */
public class IntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    private static final String SERVER_URL = "http://127.0.0.1:18080";
    private static final String VALID_TOKEN = "test-client-token";

    private static ProcessManager pm;
    private static MockApiServer mockApiServer;
    private static OkHttpClient httpClient;

    @BeforeAll
    static void setUp() throws Exception {
        logger.info("Starting integration test setup...");

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // Start mock API server
        mockApiServer = new MockApiServer(9999);
        mockApiServer.start();
        logger.info("Mock API server started on port 9999");

        // Get JAR paths
        String serverJar = findJar("forward-server");
        String agentJar = findJar("forward-agent");

        logger.info("Server JAR: {}", serverJar);
        logger.info("Agent JAR: {}", agentJar);

        // Start server and agent
        pm = new ProcessManager();
        String configDir = getConfigDir();
        pm.startServer(serverJar, configDir + "/server.yaml");
        logger.info("Server started on port 18080");

        pm.startAgent(agentJar, configDir + "/agent.yaml");
        logger.info("Agent started");

        Thread.sleep(3000);
        logger.info("Setup complete, starting tests");
    }

    private static String getConfigDir() {
        String configDir = System.getProperty("user.dir");
        if (configDir.contains("integration-test")) {
            configDir = configDir.substring(0, configDir.indexOf("integration-test")) + "integration-test/src/test/resources/config";
        } else {
            configDir = configDir + "/integration-test/src/test/resources/config";
        }
        return configDir.replace("\\", "/");
    }

    @AfterAll
    static void tearDown() {
        logger.info("Tearing down integration test...");
        if (pm != null) {
            pm.stopAll();
        }
        if (mockApiServer != null) {
            mockApiServer.stop();
        }
        logger.info("Tear down complete");
    }

    private static String findJar(String prefix) throws IOException {
        String rootPath = System.getProperty("project.root",
            System.getProperty("user.dir").replace("\\", "/"));
        if (rootPath.contains("integration-test")) {
            rootPath = rootPath.substring(0, rootPath.indexOf("integration-test"));
        }
        if (!rootPath.endsWith("/")) {
            rootPath = rootPath + "/";
        }

        String[] dirs = {
            rootPath + "server/build/libs/",
            rootPath + "agent/build/libs/"
        };

        for (String dirPath : dirs) {
            java.io.File dir = new java.io.File(dirPath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles((d, name) ->
                    name.startsWith(prefix) && name.endsWith(".jar") && !name.contains("-sources"));
                if (files != null && files.length > 0) {
                    java.io.File latest = files[0];
                    for (java.io.File f : files) {
                        if (f.lastModified() > latest.lastModified()) {
                            latest = f;
                        }
                    }
                    return latest.getAbsolutePath();
                }
            }
        }
        throw new IOException("Could not find JAR for: " + prefix);
    }

    // ========== Server Tests ==========

    @Test
    void testServerHealthCheck() throws IOException {
        Request request = new Request.Builder()
                .url(SERVER_URL + "/health")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Health check response: {}", response.code());
            assertNotNull(response);
        }
    }

    @Test
    void testServerNotFound() throws IOException {
        Request request = new Request.Builder()
                .url(SERVER_URL + "/nonexistent")
                .addHeader("X-Auth-Token", VALID_TOKEN)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Not found response: {}", response.code());
            assertEquals(404, response.code(), "Should return 404 for nonexistent endpoint");
        }
    }

    // ========== Authentication Tests ==========

    @Test
    void testChatCompletionsWithoutToken() throws IOException {
        String jsonBody = "{" +
                "\"model\": \"test-model\"," +
                "\"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}]" +
                "}";

        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("No token response: {}", response.code());
            assertEquals(401, response.code(), "Should return 401 without token");
        }
    }

    @Test
    void testChatCompletionsWithInvalidToken() throws IOException {
        String jsonBody = "{" +
                "\"model\": \"test-model\"," +
                "\"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}]" +
                "}";

        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Auth-Token", "invalid-token")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Invalid token response: {}", response.code());
            assertEquals(401, response.code(), "Should return 401 with invalid token");
        }
    }

    // ========== Chat Completions Tests ==========

    @Test
    void testChatCompletionsWithValidToken() throws IOException {
        String jsonBody = "{" +
                "\"model\": \"test-model\"," +
                "\"messages\": [{\"role\": \"user\", \"content\": \"Hello!\"}]," +
                "\"stream\": false" +
                "}";

        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Auth-Token", VALID_TOKEN)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Chat completions response: {}", response.code());
            String body = response.body().string();
            logger.info("Response body: {}", body);

            assertEquals(200, response.code(), "Should return 200 with valid token");
            assertNotNull(body);
            assertTrue(body.contains("chatcmpl-mock") || body.contains("choices"));
        }
    }

    // ========== Embeddings Tests ==========

    @Test
    void testEmbeddingsWithValidToken() throws IOException {
        String jsonBody = "{" +
                "\"model\": \"text-embedding-ada-002\"," +
                "\"input\": \"Hello world\"" +
                "}";

        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/embeddings")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Auth-Token", VALID_TOKEN)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Embeddings response: {}", response.code());
            String body = response.body().string();
            logger.info("Response body: {}", body);

            assertEquals(200, response.code(), "Should return 200 with valid token");
            assertNotNull(body);
            assertTrue(body.contains("embedding") || body.contains("data"));
        }
    }

    // ========== Models Tests ==========

    @Test
    void testListModels() throws IOException {
        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/models")
                .addHeader("X-Auth-Token", VALID_TOKEN)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("List models response: {}", response.code());
            String body = response.body().string();
            logger.info("Response body: {}", body);

            assertEquals(200, response.code(), "Should return 200 for list models");
            assertNotNull(body);
            assertTrue(body.contains("data") || body.contains("models"));
        }
    }
}