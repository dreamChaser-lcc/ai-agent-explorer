# 04 · 长连接与 SSE——项目在用的技术

> **状态**：⬜ 未学习 ｜ **下一课**：05·HTTPS 握手
> **项目强关联**：TaskProgressStreamService 的 SSE 进度推送

---

## 一、短连接的浪费与 Keep-Alive

```text
HTTP/1.0：一次请求 = 建一次 TCP（三次握手）→ 断——开销大
HTTP/1.1 Keep-Alive：建一次 TCP，串行跑多个请求（复用连接）
HTTP/2：多路复用——同一 TCP 上【并发】多个流（解决了队头阻塞）
前端对照：axios keep-alive / 浏览器的 6 个并发连接限制
```

## 二、SSE（Server-Sent Events）——服务器单向推送

```text
本质：一个【不关闭的 HTTP 响应】，服务器不停往里写数据
Content-Type: text/event-stream          ← 关键头
data: {"progress":30}\n\n                 ← 事件格式（data: 开头，双换行结束）
```

**三个特征**：

```text
① 单向（服务端→客户端），客户端只能靠"断开"表达"不听了"
② 文本协议（基于 HTTP，过网关/代理零阻力）
③ 自带重连（浏览器 EventSource 断线自动重试）
```

## 三、项目活例（TaskProgressStreamService）

```java
// 注册：客户端 GET /api/tasks/{id}/progress/stream
// → SseEmitter 存入 Map<UUID, SseEmitter>（ConcurrentHashMap——J3 §18 的场景！）
// 异步任务推进 → emitter.send(...) → 前端实时看到进度
// 完成/超时 → remove（多线程同时摸这张 Map → 并发容器的原因）

前端一行：new EventSource('/api/tasks/xx/progress/stream')
```

## 四、SSE vs WebSocket（高频对比）

| | SSE | WebSocket |
|---|---|---|
| 方向 | 单向（服务→客） | 双向 |
| 协议 | 纯 HTTP | 升级协议（101） |
| 重连 | 浏览器自动 | 要自己写 |
| 适合 | 进度/通知/行情推送 | 聊天/协作编辑 |
| 项目 | **任务进度推送** | 暂无需 |

**选型一句话**：只要"服务端说话"，SSE 比 WebSocket 简单一截——够用哲学。

## 面试速答

```text
Q：SSE 为什么用 ConcurrentHashMap 存 Emitter？
A：HTTP 线程注册、异步线程推送移除——共享+可变+多线程（J8 三条件）。
Q：SSE 断了怎么办？ A：EventSource 自动重连；服务端要处理 onCompletion/onTimeout 清理。
```

## 一句话

```text
Keep-Alive 复用连接省握手；SSE = 不关门的 HTTP 响应做单向推送。
项目进度推送就是 SSE；双向需求才上 WebSocket。
```
