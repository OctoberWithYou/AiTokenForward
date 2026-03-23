package org.ljc.configtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 模型转发系统 - Web 配置工具
 * 简体中文图形界面 (Web技术)
 */
public class ConfigTool {

    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new WebHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("===========================================");
        System.out.println("  AI 模型转发系统 - 配置工具");
        System.out.println("===========================================");
        System.out.println("");
        System.out.println("配置工具已启动！");
        System.out.println("");
        System.out.println("请在浏览器中打开: http://localhost:" + PORT);
        System.out.println("");
        System.out.println("按 Ctrl+C 停止服务");
    }

    static class WebHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/") || path.equals("/index.html")) {
                sendHtml(exchange);
            } else if (path.equals("/api/save-server")) {
                handleSaveServer(exchange);
            } else if (path.equals("/api/save-agent")) {
                handleSaveAgent(exchange);
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        }

        private void sendHtml(HttpExchange exchange) throws IOException {
            String html = "<!DOCTYPE html>\n" +
                    "<html lang=\"zh-CN\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>AI 模型转发系统 - 配置工具</title>\n" +
                    "    <style>\n" +
                    "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                    "        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #f5f5f5; padding: 20px; }\n" +
                    "        .container { max-width: 900px; margin: 0 auto; }\n" +
                    "        h1 { text-align: center; color: #333; margin-bottom: 20px; }\n" +
                    "        .card { background: white; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" +
                    "        .card h2 { color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; margin-bottom: 20px; }\n" +
                    "        .form-group { margin-bottom: 15px; }\n" +
                    "        .form-group label { display: block; margin-bottom: 5px; color: #666; font-weight: bold; }\n" +
                    "        .form-group input, .form-group textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }\n" +
                    "        .form-group textarea { font-family: monospace; min-height: 120px; }\n" +
                    "        .form-row { display: flex; gap: 20px; }\n" +
                    "        .form-row .form-group { flex: 1; }\n" +
                    "        .btn-group { display: flex; gap: 10px; justify-content: center; margin-top: 20px; }\n" +
                    "        .btn { padding: 12px 30px; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; transition: background 0.3s; }\n" +
                    "        .btn-primary { background: #4CAF50; color: white; }\n" +
                    "        .btn-primary:hover { background: #45a049; }\n" +
                    "        .btn-secondary { background: #2196F3; color: white; }\n" +
                    "        .btn-secondary:hover { background: #1976D2; }\n" +
                    "        .checkbox-group { display: flex; align-items: center; gap: 10px; }\n" +
                    "        .checkbox-group input { width: auto; }\n" +
                    "        #message { position: fixed; top: 20px; right: 20px; padding: 15px 25px; border-radius: 4px; display: none; z-index: 1000; }\n" +
                    "        #message.success { background: #4CAF50; color: white; }\n" +
                    "        #message.error { background: #f44336; color: white; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class=\"container\">\n" +
                    "        <h1>🤖 AI 模型转发系统 - 配置工具</h1>\n" +
                    "\n" +
                    "        <!-- 服务器配置 -->\n" +
                    "        <div class=\"card\">\n" +
                    "            <h2>🖥️ 服务器配置</h2>\n" +
                    "            <div class=\"form-row\">\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>监听地址</label>\n" +
                    "                    <input type=\"text\" id=\"serverHost\" value=\"0.0.0.0\">\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>监听端口</label>\n" +
                    "                    <input type=\"number\" id=\"serverPort\" value=\"8080\">\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-group checkbox-group\">\n" +
                    "                <input type=\"checkbox\" id=\"sslEnabled\">\n" +
                    "                <label>启用HTTPS</label>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-row\">\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>密钥库文件</label>\n" +
                    "                    <input type=\"text\" id=\"keyStore\" value=\"server.jks\">\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>密钥库密码</label>\n" +
                    "                    <input type=\"password\" id=\"keyStorePassword\" value=\"changeit\">\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-row\">\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>Token文件路径</label>\n" +
                    "                    <input type=\"text\" id=\"tokenFile\" value=\"config/external-token.txt\">\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>认证Header名称</label>\n" +
                    "                    <input type=\"text\" id=\"headerName\" value=\"X-Auth-Token\">\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-group\">\n" +
                    "                <label>API Keys (每行一个)</label>\n" +
                    "                <textarea id=\"apiKeys\">sk-forward-key-001\nsk-forward-key-002</textarea>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-row\">\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>Agent连接Token</label>\n" +
                    "                    <input type=\"text\" id=\"agentToken\" value=\"agent-secret-token-12345\">\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>最大Agent数</label>\n" +
                    "                    <input type=\"number\" id=\"maxAgents\" value=\"10\">\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            <div class=\"btn-group\">\n" +
                    "                <button class=\"btn btn-primary\" onclick=\"saveServerConfig()\">💾 保存服务器配置</button>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "\n" +
                    "        <!-- 客户端配置 -->\n" +
                    "        <div class=\"card\">\n" +
                    "            <h2>📱 客户端配置</h2>\n" +
                    "            <div class=\"form-row\">\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>WebSocket服务器地址</label>\n" +
                    "                    <input type=\"text\" id=\"agentServerUrl\" value=\"ws://127.0.0.1:8081/agent\">\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <label>连接Token</label>\n" +
                    "                    <input type=\"text\" id=\"agentTokenClient\" value=\"agent-secret-token-12345\">\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            <div class=\"form-group\">\n" +
                    "                <label>模型配置 (JSON格式)</label>\n" +
                    "                <textarea id=\"models\">[\n" +
                    "  {\n" +
                    "    \"id\": \"gpt-4\",\n" +
                    "    \"provider\": \"openai\",\n" +
                    "    \"endpoint\": \"https://api.openai.com/v1\",\n" +
                    "    \"apiKey\": \"your-api-key\"\n" +
                    "  },\n" +
                    "  {\n" +
                    "    \"id\": \"text-embedding-ada-002\",\n" +
                    "    \"provider\": \"openai\",\n" +
                    "    \"endpoint\": \"https://api.openai.com/v1\",\n" +
                    "    \"apiKey\": \"your-api-key\"\n" +
                    "  }\n" +
                    "]</textarea>\n" +
                    "            </div>\n" +
                    "            <div class=\"btn-group\">\n" +
                    "                <button class=\"btn btn-secondary\" onclick=\"saveAgentConfig()\">💾 保存客户端配置</button>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "\n" +
                    "        <!-- 使用说明 -->\n" +
                    "        <div class=\"card\">\n" +
                    "            <h2>📖 使用说明</h2>\n" +
                    "            <div style=\"line-height: 1.8; color: #666;\">\n" +
                    "                <p><strong>启动服务器:</strong> <code>java -jar forward-server.jar --config server.yaml</code></p>\n" +
                    "                <p><strong>启动客户端:</strong> <code>java -jar forward-agent.jar --config agent.yaml</code></p>\n" +
                    "                <p><strong>WebSocket端口:</strong> HTTP端口 + 1 (例如: 8080 -> 8081)</p>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <div id=\"message\"></div>\n" +
                    "\n" +
                    "    <script>\n" +
                    "        function showMessage(text, type) {\n" +
                    "            const msg = document.getElementById('message');\n" +
                    "            msg.textContent = text;\n" +
                    "            msg.className = type;\n" +
                    "            msg.style.display = 'block';\n" +
                    "            setTimeout(() => msg.style.display = 'none', 3000);\n" +
                    "        }\n" +
                    "\n" +
                    "        function saveServerConfig() {\n" +
                    "            const config = {\n" +
                    "                server: {\n" +
                    "                    host: document.getElementById('serverHost').value,\n" +
                    "                    port: parseInt(document.getElementById('serverPort').value),\n" +
                    "                    ssl: {\n" +
                    "                        enabled: document.getElementById('sslEnabled').checked,\n" +
                    "                        keyStore: document.getElementById('keyStore').value,\n" +
                    "                        keyStorePassword: document.getElementById('keyStorePassword').value\n" +
                    "                    }\n" +
                    "                },\n" +
                    "                externalClient: {\n" +
                    "                    tokenFile: document.getElementById('tokenFile').value,\n" +
                    "                    headerName: document.getElementById('headerName').value\n" +
                    "                },\n" +
                    "                auth: {\n" +
                    "                    apiKeys: document.getElementById('apiKeys').value.split('\\n').filter(k => k.trim())\n" +
                    "                },\n" +
                    "                agent: {\n" +
                    "                    connection: {\n" +
                    "                        token: document.getElementById('agentToken').value,\n" +
                    "                        maxAgents: parseInt(document.getElementById('maxAgents').value)\n" +
                    "                    }\n" +
                    "                }\n" +
                    "            };\n" +
                    "\n" +
                    "            fetch('/api/save-server', {\n" +
                    "                method: 'POST',\n" +
                    "                headers: {'Content-Type': 'application/json'},\n" +
                    "                body: JSON.stringify(config)\n" +
                    "            }).then(r => r.json()).then(d => {\n" +
                    "                if (d.success) showMessage('✅ 服务器配置已保存: ' + d.file, 'success');\n" +
                    "                else showMessage('❌ 保存失败: ' + d.error, 'error');\n" +
                    "            });\n" +
                    "        }\n" +
                    "\n" +
                    "        function saveAgentConfig() {\n" +
                    "            const config = {\n" +
                    "                server: {\n" +
                    "                    url: document.getElementById('agentServerUrl').value,\n" +
                    "                    token: document.getElementById('agentTokenClient').value\n" +
                    "                },\n" +
                    "                models: JSON.parse(document.getElementById('models').value)\n" +
                    "            };\n" +
                    "\n" +
                    "            fetch('/api/save-agent', {\n" +
                    "                method: 'POST',\n" +
                    "                headers: {'Content-Type': 'application/json'},\n" +
                    "                body: JSON.stringify(config)\n" +
                    "            }).then(r => r.json()).then(d => {\n" +
                    "                if (d.success) showMessage('✅ 客户端配置已保存: ' + d.file, 'success');\n" +
                    "                else showMessage('❌ 保存失败: ' + d.error, 'error');\n" +
                    "            }).catch(e => showMessage('❌ JSON格式错误: ' + e.message, 'error'));\n" +
                    "        }\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void handleSaveServer(HttpExchange exchange) throws IOException {
            String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                    .lines().collect(Collectors.joining("\n"));

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> config = mapper.readValue(body, Map.class);

            String fileName = "server.yaml";
            mapper.writeValue(new File(fileName), config);

            String response = "{\"success\":true,\"file\":\"" + fileName + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
        }

        private void handleSaveAgent(HttpExchange exchange) throws IOException {
            String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                    .lines().collect(Collectors.joining("\n"));

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> config = mapper.readValue(body, Map.class);

            String fileName = "agent.yaml";
            mapper.writeValue(new File(fileName), config);

            String response = "{\"success\":true,\"file\":\"" + fileName + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
        }
    }
}