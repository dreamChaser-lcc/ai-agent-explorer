# 06 · 从 URL 到响应——浏览器敲下回车之后

> **状态**：⬜ 未学习 ｜ **本目录收官课**

---

## 全链路十步（一张图）

```text
你输入 http://localhost:8080/api/tasks/123
  ① URL 解析（协议/主机/端口/路径）
  ② DNS 解析：localhost → 127.0.0.1（外网域名才走缓存→hosts→运营商→递归查询）
  ③ TCP 三次握手：SYN → SYN+ACK → ACK（连上 8080）
  ④ （https 才有）TLS 握手——05 课
  ⑤ 浏览器构造 HTTP 请求报文——01 课的三段式
  ⑥ 服务端接收：Filter 链 → DispatcherServlet → Controller
     （SP3 MVC 全流程 = 这一格的放大图）
  ⑦ 业务执行：Service→DAO→DB（响应链路：Entity→VO 翻译）
  ⑧ 响应报文回传：状态码+头+体——03 课
  ⑨ 浏览器解析渲染（JSON → 页面逻辑处理）
  ⑩ 连接按 Keep-Alive 复用/超时关闭——04 课
```

## 服务端内部的一格放大（对照 SP3）

```text
[Filter 链] TokenAuth(1)→BodyCaching(2)   ← 07 责任链
  → [DispatcherServlet] 找 Handler（HandlerMapping）
  → [Interceptor 前置] RequestTraceInterceptor
  → [Controller] @RestController          ← JSON 反序列化 CreateRequest
  → [Service] 业务编排                     ← 事务/乐观锁在此层
  → [DAO/Repository] @Repository          ← 异常翻译在代理层（J4）
  → [全局异常处理器] 出事时接管             ← 404/409/500 的产地（03 课）
  → 原路返回，@ResponseBody 序列化 VO
```

**一门课串起全项目**：这一格放大图里出现的技术，正是
J4（异常翻译）/ 07 责任链（Filter）/ SP3（MVC）/ 场景④（幂等）的全体出场名单。

## 面试速答

```text
Q：三次握手为什么不是两次？ A：防止旧的失效连接请求"复活"——
   两次握手下，历史 SYN 会让服务端白建连接。
Q：为什么 DNS 解析有多级缓存？ A：浏览器→系统→路由器→运营商→根，
   每级都是"能用缓存绝不走网"的就近原则。
```

## 一句话

```text
URL→响应 = DNS（找到它）→TCP/TLS（连上并加密）→HTTP 报文（对话）→
服务端流水线（Filter→MVC→异常翻译）→原路返回。
全项目学过的技术在这条链上各就各位。
```
