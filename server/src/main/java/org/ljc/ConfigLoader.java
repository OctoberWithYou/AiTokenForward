package org.ljc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.ljc.config.ServerConfig;

import java.io.File;
import java.io.IOException;

public class ConfigLoader {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static ServerConfig loadServerConfig(String configPath) throws IOException {
        return yamlMapper.readValue(new File(configPath), ServerConfig.class);
    }

    public static String toJson(Object obj) throws IOException {
        return jsonMapper.writeValueAsString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        return jsonMapper.readValue(json, clazz);
    }

    public static <T> T fromYaml(String yaml, Class<T> clazz) throws IOException {
        return yamlMapper.readValue(yaml, clazz);
    }
}