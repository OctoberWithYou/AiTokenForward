package org.ljc.configtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigToolApplication {

    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        Server server = new Server(PORT);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // API Servlet
        context.addServlet(new ServletHolder(new ApiServlet()), "/api/*");

        // Static files from React build
        String staticPath = System.getProperty("user.dir") + "/config-tool/src/main/frontend/dist";
        context.setResourceBase(staticPath);
        context.addServlet(new ServletHolder(new DefaultServlet()), "/*");

        server.start();

        System.out.println("===========================================");
        System.out.println("  AI 模型转发系统 - 配置工具");
        System.out.println("===========================================");
        System.out.println();
        System.out.println("配置工具已启动！");
        System.out.println();
        System.out.println("请在浏览器中打开: http://localhost:" + PORT);
        System.out.println();
        System.out.println("按 Ctrl+C 停止服务");

        server.join();
    }

    static class ApiServlet extends HttpServlet {
        private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        private static final String SERVER_CONFIG = "server.yaml";
        private static final String AGENT_CONFIG = "agent.yaml";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");

            String pathInfo = req.getPathInfo();

            if (pathInfo == null || pathInfo.equals("/")) {
                resp.setStatus(404);
                return;
            }

            try {
                if (pathInfo.equals("/server")) {
                    Map<String, Object> config = getServerConfig();
                    yamlMapper.writeValue(resp.getWriter(), config);
                } else if (pathInfo.equals("/agent")) {
                    Map<String, Object> config = getAgentConfig();
                    yamlMapper.writeValue(resp.getWriter(), config);
                } else {
                    resp.setStatus(404);
                }
            } catch (Exception e) {
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");

            String pathInfo = req.getPathInfo();
            Map<String, Object> response = new HashMap<>();

            try {
                Map<String, Object> body = yamlMapper.readValue(req.getInputStream(), Map.class);

                if ("/server".equals(pathInfo)) {
                    yamlMapper.writeValue(new File(SERVER_CONFIG), body);
                    response.put("success", true);
                    response.put("file", SERVER_CONFIG);
                } else if ("/agent".equals(pathInfo)) {
                    yamlMapper.writeValue(new File(AGENT_CONFIG), body);
                    response.put("success", true);
                    response.put("file", AGENT_CONFIG);
                } else {
                    resp.setStatus(404);
                    return;
                }
                yamlMapper.writeValue(resp.getWriter(), response);
            } catch (Exception e) {
                response.put("success", false);
                response.put("error", e.getMessage());
                resp.setStatus(500);
                yamlMapper.writeValue(resp.getWriter(), response);
            }
        }

        private Map<String, Object> getServerConfig() throws IOException {
            File file = new File(SERVER_CONFIG);
            if (!file.exists()) {
                return getDefaultServerConfig();
            }
            return yamlMapper.readValue(file, Map.class);
        }

        private Map<String, Object> getAgentConfig() throws IOException {
            File file = new File(AGENT_CONFIG);
            if (!file.exists()) {
                return getDefaultAgentConfig();
            }
            return yamlMapper.readValue(file, Map.class);
        }

        private Map<String, Object> getDefaultServerConfig() {
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> server = new HashMap<>();
            server.put("host", "0.0.0.0");
            server.put("port", 8080);
            Map<String, Object> ssl = new HashMap<>();
            ssl.put("enabled", false);
            ssl.put("keyStore", "server.jks");
            ssl.put("keyStorePassword", "changeit");
            server.put("ssl", ssl);
            config.put("server", server);

            Map<String, Object> externalClient = new HashMap<>();
            externalClient.put("tokenFile", "config/external-token.txt");
            externalClient.put("headerName", "X-Auth-Token");
            config.put("externalClient", externalClient);

            Map<String, Object> auth = new HashMap<>();
            auth.put("apiKeys", java.util.List.of("sk-forward-key-001", "sk-forward-key-002"));
            config.put("auth", auth);

            Map<String, Object> agent = new HashMap<>();
            Map<String, Object> connection = new HashMap<>();
            connection.put("token", "agent-secret-token-12345");
            connection.put("maxAgents", 10);
            agent.put("connection", connection);
            config.put("agent", agent);

            return config;
        }

        private Map<String, Object> getDefaultAgentConfig() {
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> server = new HashMap<>();
            server.put("url", "ws://127.0.0.1:8081/agent");
            server.put("token", "agent-secret-token-12345");
            config.put("server", server);

            Map<String, Object> model1 = new HashMap<>();
            model1.put("id", "gpt-4");
            model1.put("provider", "openai");
            model1.put("endpoint", "https://api.openai.com/v1");
            model1.put("apiKey", "your-api-key");

            Map<String, Object> model2 = new HashMap<>();
            model2.put("id", "text-embedding-ada-002");
            model2.put("provider", "openai");
            model2.put("endpoint", "https://api.openai.com/v1");
            model2.put("apiKey", "your-api-key");

            config.put("models", java.util.List.of(model1, model2));
            return config;
        }
    }
}