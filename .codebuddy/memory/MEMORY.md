# MEMORY.md —— 跨会话长期记忆

> 存放稳定的用户偏好、项目约定等跨会话事实；日常细节见同目录 `YYYY-MM-DD.md`。

## 用户画像与学习背景

- 前端（JS/TS）背景，正在系统学习 Java 后端 + 云原生（Docker / K8s / 微服务）。
- 学习项目：`ai-agent-explorer`（真实项目，Spring Boot 后端）+ `micro-lab`（微服务教学项目：user-service / order-service / gateway）。
- 学习环境：Windows 开发机 + VMware 双节点 Ubuntu（node1=lccserver、node2=lccserver-node2）+ K3s 双节点集群（node2 为 server）。

## 教学 / 文档偏好（重要，持续生效）

1. **文档要详细展开，不要要点罗列**（2026-09-04/09-05 反馈）。
2. **总结性内容尽量用"对照表 / 对比表格"呈现**（2026-09-19 反馈："总结的时候最好有对照表补充，更助于理解"）。
3. **"偷懒步骤"必须提前说明**：教学中涉及"不符合企业级常规"的操作（关防火墙、免密、root 直登、跳过安全配置等），必须提前说明并给出【企业级 vs 简化版】选项让用户选择，不能默认走简化路径（2026-09-07 明确约定）。
4. 教学惯例：命令**逐参数解释**（一个 flag 都不省）；排障讲"证据链"（用数据定位，不靠猜）。

## 环境与工程约定

- 学习复盘文档：`docs/服务架构/` 下《虚拟机DockerK8s实战复盘.md》（基础设施，踩坑用 P 编号）与《微服务实战复盘.md》（微服务 8 站 + 探针篇）——持续维护、每次实战后同步。
- micro-lab 端口约定：user-service 8081 / order-service 8082 / gateway 9090（NodePort 30090）；Nacos NodePort 30048/31048。
- 两节点 IP（2026-09-19 起 Netplan 静态化）：node1=192.168.157.128、node2=192.168.157.129（node2 待最终复核）；VMware DHCP 池已挪 `.150+`。
- K3s 镜像搬运约定：两台节点都要导入、命名空间 `k8s.io`；**同名 tag 覆盖有"旧 tar 冒充新镜像"风险**（见微服务复盘 9.9）——save 后核对 tar 时间戳、import 后核对 digest 变化。
- 顺序铁律：改代码 → mvn package → docker build → save → scp → ctr import → rollout restart（缺一环 Pod 跑旧代码）。
