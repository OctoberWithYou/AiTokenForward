package org.ljc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class AuthManager {

    private static final Logger logger = LoggerFactory.getLogger(AuthManager.class);

    private final Set<String> validApiKeys = new HashSet<>();
    private String externalClientToken = null;
    private String tokenFilePath = null;
    private String headerName = "X-Auth-Token";

    public void init(Set<String> apiKeys, String tokenFile, String headerName) {
        if (apiKeys != null) {
            validApiKeys.addAll(apiKeys);
            logger.info("Loaded {} API keys", validApiKeys.size());
        }

        if (tokenFile != null && !tokenFile.isEmpty()) {
            this.tokenFilePath = tokenFile;
            loadExternalToken();
        }

        if (headerName != null && !headerName.isEmpty()) {
            this.headerName = headerName;
        }

        logger.info("Auth manager initialized, header name: {}", this.headerName);
    }

    private void loadExternalToken() {
        try {
            Path path = Paths.get(tokenFilePath);
            if (Files.exists(path)) {
                String token = Files.readString(path).trim();
                if (!token.isEmpty()) {
                    this.externalClientToken = token;
                    logger.info("Loaded external client token from: {}", tokenFilePath);
                }
            } else {
                logger.warn("Token file not found: {}", tokenFilePath);
            }
        } catch (IOException e) {
            logger.error("Failed to load token file: {}", e.getMessage());
        }
    }

    public boolean validateApiKey(String apiKey) {
        return validApiKeys.contains(apiKey);
    }

    public boolean validateExternalToken(String token) {
        if (externalClientToken == null || token == null) {
            return false;
        }
        return externalClientToken.equals(token);
    }

    public String getHeaderName() {
        return headerName;
    }

    public boolean hasExternalToken() {
        return externalClientToken != null;
    }
}