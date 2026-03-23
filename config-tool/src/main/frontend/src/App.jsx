import React, { useState, useEffect } from 'react'

// 默认模型模板
const defaultModels = [
  { id: 'gpt-4', provider: 'openai', endpoint: 'https://api.openai.com/v1', apiKey: '', apiVersion: '' },
  { id: 'gpt-3.5-turbo', provider: 'openai', endpoint: 'https://api.openai.com/v1', apiKey: '', apiVersion: '' },
  { id: 'text-embedding-ada-002', provider: 'openai', endpoint: 'https://api.openai.com/v1', apiKey: '', apiVersion: '' }
]

function App() {
  const [serverConfig, setServerConfig] = useState(null)
  const [agentConfig, setAgentConfig] = useState(null)
  const [loading, setLoading] = useState(true)
  const [serversection, setServerSection] = useState({ basic: true, ssl: false, auth: false, agent: false })
  const [agentsection, setAgentSection] = useState({ server: true, models: false })
  const [starting, setStarting] = useState({ server: false, agent: false })

  useEffect(() => {
    Promise.all([
      fetch('/api/server').then(r => r.json()),
      fetch('/api/agent').then(r => r.json())
    ]).then(([server, agent]) => {
      setServerConfig(server)
      // 如果没有模型,使用默认模板
      if (!agent.models || agent.models.length === 0) {
        agent.models = [...defaultModels]
      }
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

  const startServer = async () => {
    setStarting(s => ({ ...s, server: true }))
    try {
      await fetch('/api/start-server', { method: 'POST' })
      showMessage('🚀 服务器启动中...', 'success')
    } catch (e) {
      showMessage('❌ 启动失败: ' + e.message, 'error')
    }
    setTimeout(() => setStarting(s => ({ ...s, server: false })), 5000)
  }

  const startAgent = async () => {
    setStarting(s => ({ ...s, agent: true }))
    try {
      await fetch('/api/start-agent', { method: 'POST' })
      showMessage('🚀 客户端启动中...', 'success')
    } catch (e) {
      showMessage('❌ 启动失败: ' + e.message, 'error')
    }
    setTimeout(() => setStarting(s => ({ ...s, agent: false })), 5000)
  }

  const toggleSection = (type, key) => {
    if (type === 'server') {
      setServerSection(s => ({ ...s, [key]: !s[key] }))
    } else {
      setAgentSection(s => ({ ...s, [key]: !s[key] }))
    }
  }

  // 添加模型
  const addModel = () => {
    setAgentConfig({
      ...agentConfig,
      models: [...(agentConfig.models || []), { id: '', provider: 'openai', endpoint: 'https://api.openai.com/v1', apiKey: '', apiVersion: '' }]
    })
  }

  // 删除模型
  const removeModel = (index) => {
    const newModels = [...agentConfig.models]
    newModels.splice(index, 1)
    setAgentConfig({ ...agentConfig, models: newModels })
  }

  // 更新模型
  const updateModel = (index, field, value) => {
    const newModels = [...agentConfig.models]
    newModels[index] = { ...newModels[index], [field]: value }
    setAgentConfig({ ...agentConfig, models: newModels })
  }

  const Collapsible = ({ title, description, open, onToggle, children }) => (
    <div className="collapsible">
      <div className="collapsible-header" onClick={onToggle}>
        <span className="collapse-icon">{open ? '▼' : '▶'}</span>
        <span className="collapse-title">{title}</span>
        <span className="collapse-desc">{description}</span>
      </div>
      {open && <div className="collapsible-content">{children}</div>}
    </div>
  )

  const HelpIcon = ({ text }) => (
    <span className="help-icon" title={text}>?</span>
  )

  if (loading) {
    return <div className="container"><h1>加载中...</h1></div>
  }

  return (
    <div className="container">
      <h1>🤖 AI 模型转发系统 - 配置工具</h1>

      {/* 服务器配置 */}
      <div className="card">
        <h2>🖥️ 服务器配置 (公网部署)</h2>
        <p className="card-desc">服务器部署在有公网IP的机器上,接收外部客户端的API请求</p>

        <Collapsible
          title="基本设置"
          description="必须配置"
          open={serversection.basic}
          onToggle={() => toggleSection('server', 'basic')}
        >
          <div className="form-row">
            <div className="form-group">
              <label>监听地址 <HelpIcon text="服务器绑定的IP地址,0.0.0.0表示监听所有网卡" /></label>
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
              <label>监听端口 <HelpIcon text="HTTP服务端口,客户端通过此端口访问,WebSocket自动使用port+1" /></label>
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
        </Collapsible>

        <Collapsible
          title="HTTPS安全设置 (可选)"
          description="生产环境建议启用"
          open={serversection.ssl}
          onToggle={() => toggleSection('server', 'ssl')}
        >
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
            <label>启用HTTPS加密传输</label>
          </div>
          {serverConfig?.server?.ssl?.enabled && (
            <div className="form-row">
              <div className="form-group">
                <label>密钥库文件路径 <HelpIcon text="Java Keystore文件路径,用于HTTPS加密" /></label>
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
                <label>密钥库密码 <HelpIcon text="打开密钥库的密码" /></label>
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
        </Collapsible>

        <Collapsible
          title="客户端认证设置"
          description="控制谁能访问你的服务器"
          open={serversection.auth}
          onToggle={() => toggleSection('server', 'auth')}
        >
          <div className="form-row">
            <div className="form-group">
              <label>Token文件路径 <HelpIcon text="存放客户端Token的文件路径,每行一个Token" /></label>
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
              <label>认证Header名称 <HelpIcon text="客户端传递Token的HTTP Header名称" /></label>
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
            <label>API Keys列表 <HelpIcon text="允许访问的API Key列表,每个Key占一行" /></label>
            <textarea
              value={serverConfig?.auth?.apiKeys?.join('\n') || ''}
              onChange={e => setServerConfig({
                ...serverConfig,
                auth: { ...serverConfig.auth, apiKeys: e.target.value.split('\n').filter(k => k.trim()) }
              })}
            />
          </div>
        </Collapsible>

        <Collapsible
          title="Agent连接设置"
          description="内网客户端的连接配置"
          open={serversection.agent}
          onToggle={() => toggleSection('server', 'agent')}
        >
          <div className="form-row">
            <div className="form-group">
              <label>Agent连接Token <HelpIcon text="内网Agent连接时使用的认证Token,需要与Agent配置一致" /></label>
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
              <label>最大Agent数量 <HelpIcon text="允许同时连接的最大Agent数量" /></label>
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
        </Collapsible>

        <div className="btn-group">
          <button className="btn btn-primary" onClick={saveServerConfig}>
            💾 保存服务器配置
          </button>
          <button className="btn btn-success" onClick={startServer} disabled={starting.server}>
            {starting.server ? '⏳ 启动中...' : '🚀 启动服务器'}
          </button>
        </div>
      </div>

      {/* 客户端配置 */}
      <div className="card">
        <h2>📱 客户端配置 (内网部署)</h2>
        <p className="card-desc">客户端部署在能访问AI服务的内网机器上</p>

        <Collapsible
          title="服务器连接"
          description="连接公网服务器"
          open={agentsection.server}
          onToggle={() => toggleSection('agent', 'server')}
        >
          <div className="form-row">
            <div className="form-group">
              <label>WebSocket服务器地址 <HelpIcon text="公网服务器的WebSocket地址,格式: ws://域名或IP:端口/agent" /></label>
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
              <label>连接Token <HelpIcon text="与服务器配置的Agent连接Token一致" /></label>
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
        </Collapsible>

        <Collapsible
          title="模型配置"
          description="要转发的AI模型列表"
          open={agentsection.models}
          onToggle={() => toggleSection('agent', 'models')}
        >
          <div className="model-list">
            {(agentConfig?.models || []).map((model, index) => (
              <div key={index} className="model-item">
                <div className="model-header">
                  <span>模型 #{index + 1}</span>
                  <button className="btn-remove" onClick={() => removeModel(index)}>删除</button>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>模型ID <HelpIcon text="模型的唯一标识符,客户端调用时使用,如: gpt-4" /></label>
                    <input
                      type="text"
                      value={model.id || ''}
                      onChange={e => updateModel(index, 'id', e.target.value)}
                      placeholder="如: gpt-4"
                    />
                  </div>
                  <div className="form-group">
                    <label>提供商 <HelpIcon text="AI服务提供商: openai / anthropic / azure" /></label>
                    <select
                      value={model.provider || 'openai'}
                      onChange={e => updateModel(index, 'provider', e.target.value)}
                    >
                      <option value="openai">OpenAI</option>
                      <option value="anthropic">Anthropic Claude</option>
                      <option value="azure">Azure OpenAI</option>
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>API端点 <HelpIcon text="AI服务的API地址" /></label>
                    <input
                      type="text"
                      value={model.endpoint || ''}
                      onChange={e => updateModel(index, 'endpoint', e.target.value)}
                      placeholder="如: https://api.openai.com/v1"
                    />
                  </div>
                  <div className="form-group">
                    <label>API Key <HelpIcon text="访问AI服务所需的密钥" /></label>
                    <input
                      type="password"
                      value={model.apiKey || ''}
                      onChange={e => updateModel(index, 'apiKey', e.target.value)}
                      placeholder="sk-xxx"
                    />
                  </div>
                </div>
                {model.provider === 'anthropic' && (
                  <div className="form-group">
                    <label>API版本 <HelpIcon text="Anthropic API版本,如: 2023-06-01" /></label>
                    <input
                      type="text"
                      value={model.apiVersion || ''}
                      onChange={e => updateModel(index, 'apiVersion', e.target.value)}
                      placeholder="2023-06-01"
                    />
                  </div>
                )}
                {model.provider === 'azure' && (
                  <div className="form-row">
                    <div className="form-group">
                      <label>API版本 <HelpIcon text="Azure API版本,如: 2024-02-01" /></label>
                      <input
                        type="text"
                        value={model.apiVersion || ''}
                        onChange={e => updateModel(index, 'apiVersion', e.target.value)}
                        placeholder="2024-02-01"
                      />
                    </div>
                    <div className="form-group">
                      <label>部署名称 <HelpIcon text="Azure上的部署名称" /></label>
                      <input
                        type="text"
                        value={model.deploymentName || ''}
                        onChange={e => updateModel(index, 'deploymentName', e.target.value)}
                        placeholder="gpt-4"
                      />
                    </div>
                  </div>
                )}
              </div>
            ))}
            <button className="btn btn-add" onClick={addModel}>+ 添加模型</button>
          </div>
        </Collapsible>

        <div className="btn-group">
          <button className="btn btn-secondary" onClick={saveAgentConfig}>
            💾 保存客户端配置
          </button>
          <button className="btn btn-success" onClick={startAgent} disabled={starting.agent}>
            {starting.agent ? '⏳ 启动中...' : '🚀 启动客户端'}
          </button>
        </div>
      </div>

      {/* 使用说明 */}
      <div className="card">
        <h2>📖 快速开始指南</h2>
        <div className="usage-section">
          <h3>第一步: 部署服务器</h3>
          <ol>
            <li>在有公网IP的服务器上保存服务器配置</li>
            <li>点击"启动服务器"或手动运行: <code>java -jar forward-server.jar --config server.yaml</code></li>
          </ol>
          <h3>第二步: 部署客户端</h3>
          <ol>
            <li>在内网Windows电脑上保存客户端配置</li>
            <li>配置AI模型(需要能访问外网)</li>
            <li>点击"启动客户端"或手动运行: <code>java -jar forward-agent.jar --config agent.yaml</code></li>
          </ol>
          <h3>第三步: 使用</h3>
          <ol>
            <li>客户端通过HTTP调用服务器: <code>POST http://服务器IP:8080/v1/chat/completions</code></li>
            <li>Header中添加认证: <code>X-Auth-Token: 你的token</code></li>
          </ol>
        </div>
      </div>

      <div id="message"></div>
    </div>
  )
}

export default App