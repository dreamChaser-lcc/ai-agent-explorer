# 案例 · 前端埋点日志链路——从一个 weblog.gif 请求到 Kibana

> **定位**：ES 系列实战案例——把"**日志直接写入 ES**"的完整链路讲透：前端埋点（beacon）→ 接入层 → Kafka → ES（按天索引）→ Kibana → 告警。横跨前后端，是 E1 §5"数据来源三类"里第二类（日志直接写入）的真实展开。
> **前置**：[E1·ES是什么与倒排索引](E1·ES是什么与倒排索引.md)（倒排索引 / ES 不是数据库）、场景⑥（[⑥异步升级Kafka](../业务场景实战/⑥异步升级Kafka.md)，Kafka 缓冲思想）
> **建立**：2026-11-23

---

## 1. 先看一段真实的前端埋点代码

```js
fetch(`${ywsink}/weblog.gif?md5=${str}&expires=${date}`, {
  headers: {
    'content-type': 'text/plain'
  },
  body: encodeURIComponent(JSON.stringify(Object.assign(data, { pageGuid }))),
  method: 'POST',
  keepalive: true
});
```

**它在做什么**：把用户行为/错误/性能数据，以极低成本"甩"给日志汇聚服务（`ywsink` = 日志域名）。这不是普通接口调用，而是**埋点（beacon 打点）**——每个设计细节都是踩过坑长出来的：

| 设计 | 解决什么问题 | 备注 |
|------|-------------|------|
| **`weblog.gif` 伪装成图片** | **像素打点**的历史形态：跨域限制最少、返回 204/1x1 gif 极省、CDN/代理不拦 | 现代等价物 = `navigator.sendBeacon()` |
| **`keepalive: true`** ★ | 日志常在 `pagehide`/`beforeunload`（关页/跳转）上报——普通 fetch 会被浏览器**中断**；keepalive 允许请求在页面卸载后继续完成 | 限制：body < 64KB |
| **`content-type: text/plain`** | 避免 **CORS 预检（OPTIONS）**：`application/json` 属"非简单请求"要多一次往返；`text/plain` 是"简单请求"直发 | 日志域名多、要最小化请求成本 |
| **`md5` 签名** | **防伪造/防刷**：服务端用 `md5(body + expires + secret)` 重算对比——抓不到 secret 的伪造上报会被拒 | 大厂日志平台的标配 |
| **`expires` 过期时间** | **防重放**：签名窗口过期（如 5 分钟）→ 旧请求重放也无效 | 与 md5 配合使用 |
| **`body` 里 `encodeURIComponent`** | 防特殊字符破坏纯文本 body 结构 | 配合 text/plain 约定 |
| **`pageGuid`** | **会话串联**：同一次页面访问的所有事件共享一个 GUID → ES 里可"按会话还原用户轨迹" | 排查体验问题的关键维度 |

---

## 2. 完整链路：从浏览器到 Kibana

```text
【① 前端】埋点 SDK
   攒一批事件（batch）→ 生成 md5/expires/pageGuid → beacon 上报
   （低版本浏览器降级：1x1 img / XHR；现代：sendBeacon / fetch keepalive）
        ↓
【② 接入层】Nginx / 网关
   weblog.gif 路由 → 返回 204/1x1 gif（浏览器"以为"是图片）
   + 验签（md5/expires）→ 拒绝伪造
   + 写本地日志（access log 或 自定义 body log）
        ↓
【③ 采集层】Filebeat / Logstash / 自研 consumer
   逐行解析 → 结构化 JSON（时间、事件类型、页面、设备、错误栈...）
        ↓
【④ 缓冲层】Kafka（★ 为什么日志也要 MQ）
   前端洪峰 vs ES 写入速率不匹配 → 削峰
   采集与存储解耦 → ES 维护/重建时不丢日志
        ↓
【⑤ 存储检索层】Elasticsearch
   按天建索引：weblog-2026.11.23
   mapping：事件类型/页面用 keyword，错误栈/URL 用 text，时间用 date
        ↓
【⑥ 查询展示层】Kibana
   Discover 搜索（"某用户某时段的所有事件"）+ 可视化（错误率折线/Top 页面/设备分布）
        ↓
【⑦ 告警】错误率 > 阈值 → 钉钉/邮件（Kibana Alerting / 自研）
```

**ES 在这里的角色**（对照 E1 §5 的"数据来源三类"）：

| 来源 | 有无 MySQL 真相源 | 本例 |
|------|------------------|------|
| 数据库同步 | ✅ 有 | 业务数据（报告/商品）的搜索索引 |
| **日志直接写入** ★ | ❌ 没有——日志流本身就是源头 | **本案例（weblog 埋点）** |
| 程序直接写入 | 视情况 | 埋点、点击流 |

---

## 3. 每一层的设计要点

### 3.1 前端 SDK 层（埋点的"最后一公里"）

```text
三大纪律：
① 批量：事件先进内存队列，攒够 N 条/每 5 秒发一次（减少请求数）
② 采样：非关键事件按比例采样（如 10%）——日志量直接决定成本
③ 卸载兜底：pagehide 时强制 flush——这就是 keepalive/sendBeacon 的用武之地
```

### 3.2 接入层（验签 + 落盘）

```nginx
# Nginx 思路（伪配置）
location = /weblog.gif {
    # 1×1 透明 gif 或 204
    return 204;
    # body 落 access log（$request_body 需要开启 log_format 记录）
}
```

- **验签**：`md5(body + expires + secret)` 服务端重算对比；`expires` 过期拒绝（防重放）；
- **限流**：单 IP/单 pageGuid 每秒上限（防脚本刷）；
- **为什么返回 204/gif**：浏览器不关心响应体，最小化带宽与渲染开销。

### 3.3 Kafka 缓冲层（为什么日志也要 MQ）

| 问题 | 没有 Kafka | 有 Kafka |
|------|-----------|----------|
| 前端洪峰（几万 QPS） | 直接压垮 ES 写入 | 消息堆积在 Kafka，ES 按能力消费 |
| ES 维护/重建 | 期间的日志丢失 | 积压在 Kafka，恢复后补消费 |
| 采集与存储耦合 | 采集端要处理写入失败重试 | 生产者只管发，消费者管写 |

**与场景⑥ 同一哲学**：**消息队列 = 把"同步压力"变成"可缓冲的积压"**（LAG 可监控、可重放）。

### 3.4 ES 的索引设计与 ILM（日志场景的精髓）

```json
// 索引：weblog-2026.11.23（按天滚动）
PUT /weblog-2026.11.23
{
  "mappings": {
    "properties": {
      "ts":        { "type": "date" },
      "pageGuid":  { "type": "keyword" },      // 会话串联——精确匹配
      "event":     { "type": "keyword" },      // 事件类型——聚合用
      "page":      { "type": "keyword" },      // URL——聚合 Top 页面
      "device":    { "type": "keyword" },
      "errorStack":{ "type": "text", "analyzer": "ik_max_word" },   // 全文搜索
      "message":   { "type": "text", "analyzer": "ik_max_word" }
    }
  }
}
```

**ILM（Index Lifecycle Management）**——日志成本控制的杀手锏：

```text
Hot   （7 天）  ：频繁查询，正常副本
Warm  （30 天） ：只读，降副本
Cold  （90 天） ：偶尔查，压缩
Delete（到期）  ：整索引删除 ← 比"删行"快一万倍
```

> **按天索引 + ILM 是日志场景的命门**：查某天只扫那天的索引（性能），过期整索引删除（成本）。没有 ILM 的日志平台会在半年后把磁盘吃满。

---

## 4. 为什么日志非 ES 莫属（vs MySQL）

| 需求 | MySQL | ES |
|------|-------|-----|
| 写入量（千万/天） | insert 压力大、表膨胀 | bulk 批量写天然适配 |
| 查询模式（时间+关键词+聚合） | 索引跟不上多维组合 | 倒排 + 按天索引 + aggregation |
| 生命周期 | 删历史数据麻烦（大事务） | ILM 整索引删除 |
| 结构演进 | ALTER TABLE 有风险 | 动态 mapping，字段随便加 |
| 全文搜索 | `LIKE %kw%` 全表扫 | 分词 + 相关度，毫秒级 |

> **另一条主流**：日志/指标的**聚合分析**也可用 **ClickHouse**（大宽表聚合更快更省）；ES 的优势在**全文检索 + Kibana 生态**。企业常见组合：**检索用 ES，指标报表用 ClickHouse**。

---

## 5. 常见坑速查

| # | 坑 | 说明 | 解法 |
|---|-----|------|------|
| 1 | body 超 64KB | keepalive/sendBeacon 硬限制 | 拆分上报 / 截断长错误栈 |
| 2 | keepalive 丢了 | 低版本浏览器不支持 | 降级 1x1 img / XHR（同 URL） |
| 3 | 事件字段用 text 导致聚合失败 | text 被分词切碎 | 聚合维度一律 keyword |
| 4 | 索引膨胀 | 没配 ILM / 每天索引过大 | ILM + 分片数控制 + 采样 |
| 5 | 日志含 PII（手机号/身份证） | 合规风险 | 上报前脱敏（前端正则 + 服务端二次） |
| 6 | 前端洪峰压垮接入层 | 刷接口/异常风暴 | 限流 + 采样 + Kafka 削峰 |
| 7 | md5 逻辑写在前端 | secret 暴露 = 签名作废 | 签名由**服务端**生成下发或用服务端代理上报 |
| 8 | 只记成功不记失败 | 排查缺证据 | 错误栈/网络失败也必须上报（采样可降低） |

---

## 6. 与本项目/课程的关联

| 关联 | 说明 |
|------|------|
| [E1·ES是什么与倒排索引](E1·ES是什么与倒排索引.md) §5 | "日志直接写入"是 ES 数据来源第二类 |
| 场景⑥（[⑥异步升级Kafka](../业务场景实战/⑥异步升级Kafka.md)） | 同一哲学：MQ = 把同步压力变可缓冲积压 |
| [分布式并发全景](../服务架构/分布式并发全景——有序性原子性互斥.md) §7 | DB 队列/MQ 的削峰与背压 |
| 项目类比 | 任务进度是**服务端→前端**推送（SSE）；埋点是**前端→服务端**上报——两条反向链路 |

---

## 7. 面试速答

```text
Q：前端埋点为什么要用 gif / sendBeacon / keepalive？
A：日志常在页面卸载时上报，普通请求会被中断；keepalive/sendBeacon 让卸载后仍能发出。
   gif 是像素打点的历史形态（跨域限制最少、返回 204 极省）。

Q：埋点的 md5/expires 是干什么的？
A：md5 签名防伪造（secret 在服务端），expires 防重放（时间窗过期拒绝）——日志接口是公开的，
   没有签名就会被刷爆/污染数据。

Q：日志链路为什么要 Kafka？
A：削峰（前端洪峰 vs ES 写入速率）、解耦（ES 维护不丢日志）、可重放（补消费）。

Q：为什么日志用 ES 而不是 MySQL？
A：写入量大、查询是多维组合+全文+聚合、生命周期管理（ILM 整索引删除）——
   MySQL 一条都做不好；日志也没有"事务"需求。

Q：日志索引怎么设计？
A：按天滚动（weblog-YYYY.MM.dd）+ 维度字段 keyword（聚合）+ 搜索字段 text（ik）+ date；
   配 ILM：7 天热 → 30 天温 → 到期删除。
```

---

## 8. 一句话总结

```text
前端一个 weblog.gif 请求 = 埋点上报（keepalive 卸载可发 + 免预检 + 签名防刷）；
它沿 Nginx → Kafka → ES（按天索引 + ILM）→ Kibana 落地——
这就是"日志直接写入 ES"的完整链路，与"数据库搜索副本"是同一个 ES 的两种数据来源。
```
