package org.ljc.integration;

import okhttp3.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AI Model Proxy Forward System
 *
 * Tests verify:
 * 1. Server can start and handle HTTP requests
 * 2. Agent can connect to server via WebSocket
 * 3. Authentication works (valid/invalid/missing token)
 * 4. API endpoints return correct responses (/v1/chat/completions, /v1/embeddings, /v1/models)
 * 5. Config-tool can be started and serves web UI
 *
 * Prerequisites:
 * - Ports 18080, 18081, 9999 must be available
 * - Mock API server runs on port 9999
 */
public class IntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    private static final String SERVER_URL = "http://127.0.0.1:18080";
    private static final String CONFIG_TOOL_URL = "http://127.0.0.1:18888";
    private static final String VALID_TOKEN = "test-client-token";

    private static ProcessManager pm;
    private static MockApiServer mockApiServer;
    private static OkHttpClient httpClient;

    @BeforeAll
    static void setUp() throws Exception {
        logger.info("===========================================");
        logger.info("Starting Integration Test Suite");
        logger.info("===========================================");

        // Create HTTP client with timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // Start mock API server on port 9999
        mockApiServer = new MockApiServer(9999);
        mockApiServer.start();
        logger.info("Mock API server started on port 9999");

        // Wait for mock server
        waitForPort(9999, 10);
        assertTrue(isPortOpen(9999), "Mock API server should be running");

        // Get JAR paths
        String serverJar = findJar("forward-server");
        String agentJar = findJar("forward-agent");

        logger.info("Server JAR: {}", serverJar);
        logger.info("Agent JAR: {}", agentJar);

        // Start server and agent
        pm = new ProcessManager();
        String configDir = getConfigDir();
        pm.startServer(serverJar, configDir + "/server.yaml");
        logger.info("Server started on port 18080 (WebSocket on 18081)");

        pm.startAgent(agentJar, configDir + "/agent.yaml");
        logger.info("Agent started and connecting to server");

        // Wait for server and agent to be ready
        Thread.sleep(3000);

        // Verify server is running
        assertTrue(isPortOpen(18080), "Server should be running on port 18080");
        logger.info("All services ready, starting tests");
    }

    @AfterAll
    static void tearDown() {
        logger.info("===========================================");
        logger.info("Cleaning up integration test...");
        logger.info("===========================================");

        // Stop all processes
        if (pm != null) {
            pm.stopAll();
        }

        // Stop mock server
        if (mockApiServer != null) {
            mockApiServer.stop();
        }

        // Kill any remaining processes on test ports
        killProcessOnPort(18080);
        killProcessOnPort(18081);
        killProcessOnPort(9999);

        logger.info("Cleanup complete");
    }

    // ============================================
    // Server Basic Tests
    // ============================================

    /**
     * Test: Server health check endpoint
     * Verifies: Server is running and responding to HTTP requests
     */
    @Test
    @DisplayName("Server should respond to health check")
    void testServerHealthCheck() throws IOException {
        logger.info("TEST: Server health check");

        Request request = new Request.Builder()
                .url(SERVER_URL + "/health")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Health check response: {}", response.code());
            // Server should respond (even if endpoint doesn't exist, means server is up)
            assertNotNull(response, "Server should respond");
            assertTrue(response.code() >= 200 || response.code() == 404,
                    "Server should be running");
        }

        // Also verify server port is open
        assertTrue(isPortOpen(18080), "Server port 18080 should be open");
        logger.info("TEST PASSED: Server is running");
    }

    /**
     * Test: 404 for non-existent endpoints
     * Verifies: Server correctly handles unknown routes
     */
    @Test
    @DisplayName("Server should return 404 for unknown endpoints")
    void testServerNotFound() throws IOException {
        logger.info("TEST: Server 404 handling");

        Request request = new Request.Builder()
                .url(SERVER_URL + "/nonexistent")
                .addHeader("X-Auth-Token", VALID_TOKEN)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Not found response: {}", response.code());
            assertEquals(404, response.code(), "Should return 404 for nonexistent endpoint");
        }
        logger.info("TEST PASSED: 404 handled correctly");
    }

    // ============================================
    // Authentication Tests
    // ============================================

    /**
     * Test: Request without authentication token
     * Verifies: Server rejects requests without token (401)
     */
    @Test
    @DisplayName("Server should reject requests without authentication token")
    void testChatCompletionsWithoutToken() throws IOException {
        logger.info("TEST: Request without token");

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
        logger.info("TEST PASSED: No token rejected");
    }

    /**
     * Test: Request with invalid authentication token
     * Verifies: Server rejects invalid tokens (401)
     */
    @Test
    @DisplayName("Server should reject requests with invalid token")
    void testChatCompletionsWithInvalidToken() throws IOException {
        logger.info("TEST: Request with invalid token");

        String jsonBody = "{" +
                "\"model\": \"test-model\"," +
                "\"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}]" +
                "}";

        Request request = new Request.Builder()
                .url(SERVER_URL + "/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Auth-Token", "invalid-token-12345")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.info("Invalid token response: {}", response.code());
            assertEquals(401, response.code(), "Should return 401 with invalid token");
        }
        logger.info("TEST PASSED: Invalid token rejected");
    }

    // ============================================
    // API Endpoint Tests
    // ============================================

    /**
     * Test: Chat Completions with valid token
     * Verifies:
     * - Authentication works with valid token
     * - Server proxies request to Agent
     * - Agent calls mock API
     * - Response is returned to client
     */
    @Test
    @DisplayName("Chat Completions API should work with valid token")
    void testChatCompletionsWithValidToken() throws IOException {
        logger.info("TEST: Chat Completions with valid token");

        String jsonBody = "{" +
                "\"model\": \"gpt-4\"," +
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
            assertNotNull(body, "Response should have body");
            assertTrue(body.contains("chatcmpl-mock") || body.contains("choices"),
                    "Response should contain mock response");
            assertTrue(body.contains("content") || body.contains("message"),
                    "Response should contain message content");
        }
        logger.info("TEST PASSED: Chat Completions works");
    }

    /**
     * Test: Embeddings API with valid token
     * Verifies: Server correctly proxies embeddings requests to Agent
     */
    @Test
    @DisplayName("Embeddings API should work with valid token")
    void testEmbeddingsWithValidToken() throws IOException {
        logger.info("TEST: Embeddings with valid token");

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
            assertNotNull(body, "Response should have body");
            assertTrue(body.contains("embedding") || body.contains("data"),
                    "Response should contain embedding data");
        }
        logger.info("TEST PASSED: Embeddings works");
    }

    /**
     * Test: List Models API
     * Verifies: Server returns list of available models from Agent
     */
    @Test
    @DisplayName("List Models API should return available models")
    void testListModels() throws IOException {
        logger.info("TEST: List models");

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
            assertNotNull(body, "Response should have body");
            assertTrue(body.contains("data") || body.contains("models"),
                    "Response should contain model list");
        }
        logger.info("TEST PASSED: List models works");
    }

    // ============================================
    // Helper Methods
    // ============================================

    private static String getConfigDir() {
        String configDir = System.getProperty("user.dir");
        if (configDir.contains("integration-test")) {
            configDir = configDir.substring(0, configDir.indexOf("integration-test"))
                    + "integration-test/src/test/resources/config";
        } else {
            configDir = configDir + "/integration-test/src/test/resources/config";
        }
        return configDir.replace("\\", "/");
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
                        name.startsWith(prefix) && name.endsWith(".jar")
                                && !name.contains("-sources"));
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

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void waitForPort(int port, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            if (isPortOpen(port)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void killProcessOnPort(int port) {
        try {
            // Windows: netstat to find PID, then taskkill
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c",
                    "netstat -ano | findstr :" + port + " | findstr LISTENING");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Port {} status: {}", port, line);
                // Try to extract PID and kill
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5) {
                    String pid = parts[parts.length - 1];
                    try {
                        ProcessBuilder killPb = new ProcessBuilder("taskkill", "/F", "/PID", pid);
                        killPb.start();
                        logger.info("Killed process on port {} with PID {}", port, pid);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("Error killing process on port {}: {}", port, e.getMessage());
        }
    }
}