import React, { useState, useEffect } from 'react'

function App() {
  const [serverConfig, setServerConfig] = useState(null)
  const [agentConfig, setAgentConfig] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      fetch('/api/server').then(r => r.json()),
      fetch('/api/agent').then(r => r.json())
    ]).then(([server, agent]) => {
      setServerConfig(server)
      setAgentConfig(agent)
      setLoading(false)
    })
  }, [])

  const showMessage = (text, type) => {
    const msg = document.getElementById('message')
    msg.textContent = text
    msg.className = type
    msg.style.display = 'block'
    setTimeout(() => msg.style.display = 'none', 3000)
  }

  const saveServerConfig = async () => {
    try {
      const res = await fetch('/api/server', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(serverConfig)
      })
      const data = await res.json()
      if (data.success) {
        showMessage('✅ 服务器配置已保存: ' + data.file, 'success')
      } else {
        showMessage('❌ 保存失败: ' + data.error, 'error')
      }
    } catch (e) {
      showMessage('❌ 保存失败: ' + e.message, 'error')
    }
  }

  const saveAgentConfig = async () => {
    try {
      const res = await fetch('/api/agent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agentConfig)
      })
      const data = await res.json()
      if (data.success) {
        showMessage('✅ 客户端配置已保存: ' + data.file, 'success')
      } else {
        showMessage('❌ 保存失败: ' + data.error, 'error')
      }
    } catch (e) {
      showMessage('❌ 保存失败: ' + e.message, 'error')
    }
  }

  if (loading) {
    return <div className="container"><h1>加载中...</h1></div>
  }

  return (
    <div className="container">
      <h1>🤖 AI 模型转发系统 - 配置工具</h1>

      {/* 服务器配置 */}
      <div className="card">
        <h2>🖥️ 服务器配置</h2>
        <div className="form-row">
          <div className="form-group">
            <label>监听地址</label>
            <input
              type="text"
              value={serverConfig?.server?.host || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                server: { ...serverConfig.server, host: e.target.value }
              })}
            />
          </div>
          <div className="form-group">
            <label>监听端口</label>
            <input
              type="number"
              value={serverConfig?.server?.port || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                server: { ...serverConfig.server, port: parseInt(e.target.value) }
              })}
            />
          </div>
        </div>
        <div className="form-group checkbox-group">
          <input
            type="checkbox"
            checked={serverConfig?.server?.ssl?.enabled || false}
            onChange={e => setServerConfig({
              ...serverConfig,
              server: {
                ...serverConfig.server,
                ssl: { ...serverConfig.server.ssl, enabled: e.target.checked }
              }
            })}
          />
          <label>启用HTTPS</label>
        </div>
        {serverConfig?.server?.ssl?.enabled && (
          <div className="form-row">
            <div className="form-group">
              <label>密钥库文件</label>
              <input
                type="text"
                value={serverConfig?.server?.ssl?.keyStore || ''}
                onChange={e => setServerConfig({
                  ...serverConfig,
                  server: {
                    ...serverConfig.server,
                    ssl: { ...serverConfig.server.ssl, keyStore: e.target.value }
                  }
                })}
              />
            </div>
            <div className="form-group">
              <label>密钥库密码</label>
              <input
                type="password"
                value={serverConfig?.server?.ssl?.keyStorePassword || ''}
                onChange={e => setServerConfig({
                  ...serverConfig,
                  server: {
                    ...serverConfig.server,
                    ssl: { ...serverConfig.server.ssl, keyStorePassword: e.target.value }
                  }
                })}
              />
            </div>
          </div>
        )}
        <div className="form-row">
          <div className="form-group">
            <label>Token文件路径</label>
            <input
              type="text"
              value={serverConfig?.externalClient?.tokenFile || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                externalClient: { ...serverConfig.externalClient, tokenFile: e.target.value }
              })}
            />
          </div>
          <div className="form-group">
            <label>认证Header名称</label>
            <input
              type="text"
              value={serverConfig?.externalClient?.headerName || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                externalClient: { ...serverConfig.externalClient, headerName: e.target.value }
              })}
            />
          </div>
        </div>
        <div className="form-group">
          <label>API Keys (每行一个)</label>
          <textarea
            value={serverConfig?.auth?.apiKeys?.join('\n') || ''}
            onChange={e => setServerConfig({
              ...serverConfig,
              auth: { ...serverConfig.auth, apiKeys: e.target.value.split('\n').filter(k => k.trim()) }
            })}
          />
        </div>
        <div className="form-row">
          <div className="form-group">
            <label>Agent连接Token</label>
            <input
              type="text"
              value={serverConfig?.agent?.connection?.token || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                agent: {
                  ...serverConfig.agent,
                  connection: { ...serverConfig.agent.connection, token: e.target.value }
                }
              })}
            />
          </div>
          <div className="form-group">
            <label>最大Agent数</label>
            <input
              type="number"
              value={serverConfig?.agent?.connection?.maxAgents || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                agent: {
                  ...serverConfig.agent,
                  connection: { ...serverConfig.agent.connection, maxAgents: parseInt(e.target.value) }
                }
              })}
            />
          </div>
        </div>
        <div className="btn-group">
          <button className="btn btn-primary" onClick={saveServerConfig}>
            💾 保存服务器配置
          </button>
        </div>
      </div>

      {/* 客户端配置 */}
      <div className="card">
        <h2>📱 客户端配置</h2>
        <div className="form-row">
          <div className="form-group">
            <label>WebSocket服务器地址</label>
            <input
              type="text"
              value={agentConfig?.server?.url || ''}
              onChange={e => setAgentConfig({
                ...agentConfig,
                server: { ...agentConfig.server, url: e.target.value }
              })}
            />
          </div>
          <div className="form-group">
            <label>连接Token</label>
            <input
              type="text"
              value={agentConfig?.server?.token || ''}
              onChange={e => setAgentConfig({
                ...agentConfig,
                server: { ...agentConfig.server, token: e.target.value }
              })}
            />
          </div>
        </div>
        <div className="form-group">
          <label>模型配置 (JSON格式)</label>
          <textarea
            value={JSON.stringify(agentConfig?.models || [], null, 2)}
            onChange={e => {
              try {
                const models = JSON.parse(e.target.value)
                setAgentConfig({ ...agentConfig, models })
              } catch (err) {
                // Allow invalid JSON temporarily
              }
            }}
          />
        </div>
        <div className="btn-group">
          <button className="btn btn-secondary" onClick={saveAgentConfig}>
            💾 保存客户端配置
          </button>
        </div>
      </div>

      {/* 使用说明 */}
      <div className="card">
        <h2>📖 使用说明</h2>
        <div className="usage-section">
          <p><strong>启动服务器:</strong> <code>java -jar forward-server.jar --config server.yaml</code></p>
          <p><strong>启动客户端:</strong> <code>java -jar forward-agent.jar --config agent.yaml</code></p>
          <p><strong>WebSocket端口:</strong> HTTP端口 + 1 (例如: 8080 → 8081)</p>
        </div>
      </div>

      <div id="message"></div>
    </div>
  )
}

export default App