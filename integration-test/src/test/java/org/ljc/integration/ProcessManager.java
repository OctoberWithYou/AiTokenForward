package org.ljc.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test base class that manages Server and Agent processes for integration testing
 */
public class ProcessManager {
    private static final Logger logger = LoggerFactory.getLogger(ProcessManager.class);

    private Process serverProcess;
    private Process agentProcess;
    private final List<Process> processes = new ArrayList<>();

    /**
     * Start the server process using the built JAR
     */
    public void startServer(String serverJarPath, String configPath) throws IOException {
        logger.info("Starting server with config: {}", configPath);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(serverJarPath);
        command.add("--config");
        command.add(configPath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(configPath).getParentFile().getParentFile().getParentFile());
        pb.redirectErrorStream(true);

        serverProcess = pb.start();
        processes.add(serverProcess);

        // Log output in background thread
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Server] {}", line);
                }
            } catch (IOException e) {
                logger.error("Error reading server output", e);
            }
        }).start();

        // Wait for server to start
        waitForPort("127.0.0.1", 18080, 30);
        logger.info("Server started successfully");
    }

    /**
     * Start the agent process using the built JAR
     */
    public void startAgent(String agentJarPath, String configPath) throws IOException {
        logger.info("Starting agent with config: {}", configPath);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(agentJarPath);
        command.add("--config");
        command.add(configPath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(configPath).getParentFile().getParentFile().getParentFile());
        pb.redirectErrorStream(true);

        agentProcess = pb.start();
        processes.add(agentProcess);

        // Log output in background thread
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(agentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Agent] {}", line);
                }
            } catch (IOException e) {
                logger.error("Error reading agent output", e);
            }
        }).start();

        // Wait a bit for agent to connect
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("Agent started");
    }

    /**
     * Wait for a port to be available
     */
    private void waitForPort(String host, int port, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            try {
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                socket.close();
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logger.warn("Timeout waiting for port {}:{}", host, port);
    }

    /**
     * Stop all processes
     */
    public void stopAll() {
        for (Process p : processes) {
            try {
                p.destroy();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("All processes stopped");
    }
}