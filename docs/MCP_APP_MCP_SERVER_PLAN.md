# App 内嵌 MCP 服务端方案

## 目标
- 在 App 内提供符合 MCP 协议的工具端点，供 LLM 调用现有自动化能力（无障碍/脚本引擎/截图/OCR/应用控制）。
- 默认安全、可控：仅本地或受信网段监听，强制鉴权、超时和速率控制，可随时停用。
- 采用 Android 最佳实践：前台 Service 保活，协程结构化并发，配置持久化（DataStore），UI 开关可见。

## 架构
- 传输层：Ktor CIO + WebSockets/HTTP，默认监听 127.0.0.1，可配置网段与端口。
- 协议层：MCP envelope；仅暴露工具（无资源通道），统一响应格式 `{ok|error, data?, errorCode?}`。
- 执行层：复用现有组件——脚本引擎 v6/v7、automator 无障碍/手势、截图与 OCR、App 控制；脚本执行跑在独立协程/线程池，支持取消。
- 保活与可视化：前台 Service + 通知显示“LLM 控制中”，设置页可一键停用。

## 工具接口（首版）
- `device_info`：设备、前台 Activity、屏幕方向、locale。
- `run_script(script, mode=v7, timeout, args?)` → jobId；`job_status(jobId)`；`cancel_job(jobId)`。
- `tap(x,y)` / `swipe(x1,y1,x2,y2,duration)`。
- `find_element(criteria{text/id/desc/class}, timeout)` → bounds/center。
- `screenshot(asBase64?=false)` → 临时路径或 base64。
- `ocr(source=screenshot|path)` → 文本+位置信息。
- `app_control(action=launch/force_stop/bring_to_front, package)`。
- `logs(recent=N)`：最近调用/脚本输出。
- 原“资源”工具化：`get_ui_tree()`（界面层级 JSON，节流）; `get_recent_screenshot(asBase64?=false)`（最近截图元信息/路径或 base64）。

## 安全与限制
- 鉴权：必需 token；监听地址/端口可配；可选仅在充电/解锁时启用。
- 访问控制：文件白名单（仅 App 沙箱），禁止通配读写。
- 资源保护：每工具强制超时；并发数/速率限制；大 payload（截图/base64/UI 树）做压缩或节流。
- 观测：操作与错误日志；可选脱敏模式；调试模式下打印请求。

## 组件设计
- `McpServer`：启动/停止、路由注册、拦截器（鉴权/速率/超时）。
- `ToolRegistry`：工具注册与分发，统一校验与错误码。
- `ScriptRunner`：封装 v6/v7 执行，job 跟踪、取消、日志缓冲。
- `AutomationBridge`：手势/查找/截图/OCR/App 控制门面，复用 automator、paddleocr/MLKit。
- `Config`：端口/token/监听地址/返回格式，存 DataStore，设置页 UI 开关。
- 前台 Service：通知 + 生命周期管理。

## 实施计划
1) 依赖与骨架：引入 `ktor-server-cio`、`ktor-server-websockets`、`ktor-serialization-json`；新建 `app/.../mcp` 包，`McpServer`+`ToolRegistry`+`healthcheck`；设置页/前台 Service 开关与配置存储。
2) 基础工具：`device_info`、`screenshot`、`run_script`+`job_status`，统一响应/错误码/鉴权/超时。
3) 自动化工具：`tap`/`swipe`/`find_element`/`app_control`/`ocr`，桥接 automator 与 OCR；确保线程/协程安全。
4) 资源类工具：`get_ui_tree`、`get_recent_screenshot`，加节流与最大 payload 控制。
5) 安全与可靠性：取消/超时覆盖所有工具；并发/压力测试；文件白名单、速率限制、日志脱敏；前台 Service 行为验证（不同厂商）。
6) 测试与文档：真机验证全工具（含并发与长时间运行）；补充使用指南（启动、鉴权、工具示例）。

## 配置与 UX
- 设置页：开关、端口、监听地址、token、base64 返回开关、仅充电/解锁时启用，状态展示与错误提示。
- 通知动作：快速停用/重启服务；显示当前连接状态。

## 测试要点
- 功能：各工具输入校验、超时、取消、错误码一致性。
- 性能：并发调用（10+），截图/ocr 大 payload 压测，长时间运行内存与线程泄漏检查。
- 兼容：多 Android 版本/厂商的前台 Service、无障碍行为；网络可用性（无网时本地回环仍可用）。
