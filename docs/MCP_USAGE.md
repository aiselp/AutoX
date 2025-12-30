# MCP 模块使用说明

本文介绍新引入的 `:mcp` 模块（App 内嵌 MCP 服务端）的使用方式、接口与集成步骤。

## 能力概览
- 基于 Ktor Netty 的轻量 MCP 工具端点，默认提供：
  - `device_info`
  - `run_script` → `job_status` / `cancel_job`
  - `logs`
  - `list_apps`
  - `save_script` / `read_script` / `update_script` / `delete_script` / `rename_script`
  - `list_samples` / `read_sample`
  - `run_script_file`
  - `list_scripts` / `list_script_dirs`
  - `tap`、`swipe`、`find_element`、`find_elements`、`screenshot`、`ocr`、`app_control`、`get_foreground_app`、`get_current_activity`、`get_ui_tree`、`get_recent_screenshot`
- tools 调用返回 JSON-RPC `result.content`，其中包含文本结果与 `isError` 标记。
- 简易鉴权：请求头 `X-Token` 对比 `McpConfig.token`（为空则不校验）。

## 运行/配置
- 入口类：`org.autojs.autoxjs.mcp.McpService`（由 `McpServerService` 在 `:script` 进程中托管）
- 配置结构：`McpConfig(enabled, host, port, token, allowBase64, allowNetwork)`
- 设置页开关（推荐）：
  - 进入“设置 → MCP 服务”
  - 启用服务并配置监听地址/端口/Token
  - 局域网访问：开启“允许局域网访问”，监听地址建议填 `0.0.0.0`
  - 可选开启“允许返回 Base64”
- 手动启动（仅调试用）：

```kotlin
// 例如在 Application.onCreate 或前台 Service 中
val mcpService = McpService(applicationContext)
val cfg = McpConfig(
    enabled = true,
    host = "0.0.0.0",
    port = 27190,
    token = "your-token"
)
mcpService.start(cfg)
// 关闭：mcpService.stop()
```

> MCP 服务会以前台 Service 形式运行，通知栏会显示运行状态。

## HTTP 调用方式

### Streamable HTTP（标准 MCP JSON-RPC）
- 端点：`POST http://{host}:{port}/mcp`
- Header：`Content-Type: application/json`；`Accept: application/json, text/event-stream`；可选 `X-Token: <token>`
- Body：单条 JSON-RPC 请求（tools-only 最小实现）。
- 响应：当前实现返回 `application/json` 单响应（未启用 SSE 流式）。

示例：
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"Demo"}}}'
```

```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"tap","arguments":{"x":100,"y":200}}}'
```

### 工具调用示例（tools/call）
- `run_script`
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"run_script","arguments":{"script":"toast(\\"hi\\")","name":"hello","mode":"v7","timeoutMillis":20000}}}'
```

- `job_status`
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"job_status","arguments":{"jobId":1}}}'
```

- `cancel_job`
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"cancel_job","arguments":{"jobId":1}}}'
```

- `logs`
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Token: your-token" \
  -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"logs","arguments":{"recent":20}}}'
```

- `device_info`（无参数）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"device_info","arguments":{}}}'
```

- `list_apps`（可选过滤）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"list_apps","arguments":{"query":"auto","includeSystem":false,"limit":50}}}'
```

- `save_script`（保存到 AutoJs 默认脚本目录）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"save_script","arguments":{"name":"kuaishou_automation.js","script":"toast(\\"hello\\")","overwrite":true}}}'
```

- `run_script_file`（运行脚本目录下文件或绝对路径）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"run_script_file","arguments":{"path":"kuaishou_automation.js"}}}'
```

- `list_scripts`（列出脚本目录）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"list_scripts","arguments":{"query":"kuaishou","limit":50,"recursive":true}}}'
```

- `list_script_dirs`（列出脚本目录下的子目录）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"list_script_dirs","arguments":{"recursive":true,"limit":50}}}'
```

- `list_samples`（列出内置示例脚本）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"list_samples","arguments":{"query":"OCR","limit":20}}}'
```

- `read_sample`（读取内置示例脚本）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"read_sample","arguments":{"path":"v6/GoogleMLKit/OCR识别点击.js"}}}'
```

- `read_script`（读取脚本内容）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"read_script","arguments":{"path":"kuaishou_automation.js"}}}'
```

- `update_script`（覆盖脚本内容）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"update_script","arguments":{"path":"kuaishou_automation.js","script":"toast(\\"updated\\")"}}}'
```

- `rename_script`（重命名脚本）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":16,"method":"tools/call","params":{"name":"rename_script","arguments":{"path":"kuaishou_automation.js","newName":"kuaishou_final.js"}}}'
```

- `delete_script`（删除脚本）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"delete_script","arguments":{"path":"kuaishou_final.js"}}}'
```

- `find_elements`（批量查找 UI 节点）
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":18,"method":"tools/call","params":{"name":"find_elements","arguments":{"text":"关注","limit":5}}}'
```

- `get_foreground_app` / `get_current_activity`
```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":19,"method":"tools/call","params":{"name":"get_foreground_app","arguments":{}}}'
```

```bash
curl -X POST http://127.0.0.1:27190/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"get_current_activity","arguments":{}}}'
```
- `get_ui_tree`（紧凑格式）
返回字段说明：
- `c`: 简化类名
- `id`: resource id（可选）
- `t`: text（可选）
- `d`: contentDescription（可选）
- `p`: packageName（仅在与父节点不同或根节点时出现）
- `b`: bounds 数组 `[left, top, right, bottom]`
- `a`: 交互标记字符串（`c`=clickable, `f`=focusable, `s`=scrollable, `l`=longClickable, `d`=disabled）
- `children`: 子节点数组（无子节点时省略）
说明：默认仅保留“可交互/有文本/有描述/有 id”的节点，以及它们的直接父节点（用于保持一层结构）。

> 已接入自动化实现，如需扩展可在 `McpService.registerDefaultTools` 中注册更多工具。

## 打包与构建
- 模块已加入 `settings.gradle.kts`，并在 `app/build.gradle.kts` 依赖 `project(":mcp")`，常规 `app:assembleV7Debug` / `app:assembleV7` 会自动包含。
- 单独构建：`./gradlew :mcp:assembleDebug`
- 环境要求：JDK 17、Android SDK 34 + build-tools 34.0.0（已写入 `local.properties`）。

## 开发提示
- 服务默认监听 `0.0.0.0:27190`（允许局域网访问）；如需仅本机访问可改为 `127.0.0.1`，并务必设置 `token`。
- 运行脚本使用 `EngineController` 调度，脚本执行完后会删除临时文件。
- 占位工具返回 `NotImplemented`，可按 `DefaultTools.kt` 模式补齐，建议统一输入校验与超时控制。
- 工具调用日志：Logcat 过滤 `McpToolCall`（服务运行在 `:script` 进程）。
- `get_recent_screenshot` 在无历史截图时会自动抓取一张。
- Base64 截图默认会缩放到最长边 720px，并使用 JPEG 质量 70 以降低体积。
- `save_script` 会写入 AutoJs 当前脚本目录（对应应用设置里的“脚本路径”）。
- `list_samples` / `read_sample` 读取 AutoJs 内置示例资源（assets/sample）。
- `read_script` / `update_script` / `delete_script` / `rename_script` 仅操作脚本目录内文件（按相对路径解析）。
- `run_script_file` 会从 AutoJs 当前脚本目录解析相对路径，或接受绝对路径。
- `list_scripts` 返回脚本目录内的脚本文件列表（相对路径）。
- `list_script_dirs` 返回脚本目录内的子目录列表（相对路径）。
