# 虚拟机 / Docker / K8s 全链路实战复盘

> 整理日期：2026-09-08
> 适用对象：独立阅读者（无需原始会话即可复现全部操作）
> 内容来源：09-07 ~ 09-08 实战记录（AI Agent Explorer 项目环境 + 个人学习虚拟机）
>
> 配套资料：《虚拟机与企业级架构学习笔记.md》（理论篇，本文件是其实战篇）

---

## 📖 目录（点击章节跳转）

**正文部分**

- [一、学习目标与环境总览](#一学习目标与环境总览)（架构图 / 主机 IP / 账号）
- [二、阶段 A：VMware 虚拟机安装与克隆](#二阶段-avmware-虚拟机安装与克隆)（下载安装 / Ubuntu / SSH / 克隆 / 密钥免密）
- [三、阶段 B：容器基础（在虚拟机里练 Docker）](#三阶段-b容器基础在虚拟机里练-docker)
  - [B.1 装 Docker](#b1-装-dockerubuntu-源方案学习够用)
  - [B.2 国内网络加速配置](#b2-国内网络拉镜像必配加速)
  - [B.3 四个练手实验](#b3-四个练手实验对照学习笔记做)
  - [B.4 docker run 参数速查](#b4-docker-run-参数速查务必吃透)
  - [B.5 docker run 逐参数拆解](#b5-docker-run-逐参数拆解以实验3-mysql-为例)
  - [B.6 Volume 挂载方案详解](#b6-volume-挂载方案详解三种形式务必分清)
- [四、阶段 C：Nacos 部署](#四阶段-cnacos-部署docker-方式)
- [五、阶段 D：Docker Desktop + 项目镜像](#五阶段-ddocker-desktopwindows-开发机-项目镜像)
  - [D.1 为什么装](#d1-为什么装)
  - [D.2 WSL2 安装](#d2-wsl2-安装docker-desktop-的前置)
  - [D.3 Docker Desktop 装到 D 盘](#d3-docker-desktop-安装到-d-盘)
  - [D.4 项目多阶段 Dockerfile](#d4-项目多阶段-dockerfilebackenddockerfile)
  - [D.5 镜像交付到服务器](#d5-交付镜像部署到服务器手工-cicd)
- [六、阶段 E：K3s / K8s 编排](#六阶段-ek3s--k8s-编排装在-node2)
  - [E.1 安装 K3s](#e1-安装-k3s单节点集群)
  - [E.2 加速与镜像导入](#e2-国内加速--镜像导入k3s-用-containerd不是-docker)
  - [E.3 Deployment+Service YAML](#e3-deployment--service-yaml)
  - [E.4 四大能力实战（扩容/自愈/滚动/回滚）](#e4-四大能力实战全部亲测通过)
  - [E.5 kubectl 运维命令速查](#e5-kubectl-运维命令速查)
- [七、踩坑大全（P1-P15）](#七踩坑大全血泪经验务必先读)
- [八、关键概念速查](#八关键概念速查脱离会话也能复习)
- [九、当前环境状态清单](#九当前环境状态清单截至-2026-09-08)
- [十、未完成 / 待办学习项](#十未完成--待办学习项下次从这里续)
- [十一、推荐学习顺序回顾](#十一推荐学习顺序回顾)

**附录：命令详解大全（`-x` 逐个讲）**

- [十二、Linux 常用命令](#十二linux-常用命令)（目录文件 / 查看 / 进程资源 / 权限 / 网络 / 服务包管理 / 归档）
- [十三、SSH 与文件传输命令详解](#十三ssh-与文件传输命令详解)
- [十四、Docker 命令详解](#十四docker-命令详解按用途分组x-逐个拆)（镜像 / 容器 / 管理 / 上生产命令串）
- [十五、kubectl 命令详解](#十五kubectl-命令详解k8s-日常x-逐个拆)（查询 / 部署 / 发布回滚 / 污点 / K3s 特有）
- [十六、YAML / Dockerfile 逐行精读](#十六yaml--dockerfile-逐行精读)（Dockerfile / docker-compose / Deployment+Service 逐行解析）

---

## 一、学习目标与环境总览

### 1.1 想达成的能力

按企业级后端工程师的技能路径，从零搭出：

```
物理机 → 虚拟机 → Docker → 容器 → K8s 编排 → 镜像构建与交付
```

### 1.2 最终架构（实战完成态）

```
Windows 宿主机（开发机）
├── VMware Workstation Pro            装在 D:\softwares\VMware
│   ├── node1（lccserver）            Ubuntu 22.04 Server + Docker 全家桶
│   │     ├── my-nacos（Nacos 2.3.2 容器，8848/9848）
│   │     ├── my-mysql（MySQL 8.0 容器，3306，数据挂载）
│   │     ├── my-nginx（Nginx 容器，8080）
│   │     └── research-agent（项目镜像容器，8081）
│   └── node2（lccserver-node2）      Ubuntu 22.04 Server + Docker + K3s(K8s)
│         └── K3s 单节点集群
│               └── Deployment research-agent（1.1，2副本）→ NodePort 30081
│
└── Docker Desktop                    ［可选/开发用］装在 D:\softwares\Docker
      └── WSL2 底座 + 本地构建 research-agent:1.0 / 1.1 镜像
```

### 1.3 主机与 IP（重要：NAT 动态 IP）

- 两台虚拟机都是 VMware **NAT 模式**，重启后 IP 可能变化。
- 判断是哪台机器**一律以 `hostname` 为准**，不要凭 IP 数字猜。
- 最后一次确认：node2 = `192.168.157.128`（以 `ip addr` 实时查为准）。

```bash
hostname    # node1 是 lccserver；node2 是 lccserver-node2
ip addr     # ens33 网卡的 192.168.x.x 就是本机 IP
```

### 1.4 关键账号信息（需自行保管）

- VMware 虚拟机用户名：`lcc`（两台相同，克隆而来）
- node1 ↔ node2 已配 ed25519 免密登录（node1 → node2）
- Nacos 控制台：`http://<node1 IP>:8848/nacos`，账号 nacos/nacos（2.3 默认未开鉴权）

---

## 二、阶段 A：VMware 虚拟机安装与克隆

### A.1 下载与安装

| 软件 | 下载方式 | 安装位置 |
|------|----------|----------|
| VMware Workstation Pro | Broadcom 官网（需注册账号），个人免费 | `D:\softwares\VMware` |
| Ubuntu Server 22.04 | 清华镜像站 `mirrors.tuna.tsinghua.edu.cn/ubuntu-releases/22.04/` | ISO 放 `D:\softwares\ISO\` |

**VMware 安装要点**
- 安装路径改成 D 盘，避免占 C 盘。
- 安装前确认 BIOS 已开虚拟化：任务管理器 → 性能 → CPU → “虚拟化: 已启用”。未启用需进 BIOS 开 `Intel VT-x` / `AMD SVM`。

**创建虚拟机要点**
- 新建虚拟机 → “典型” → 选“**稍后安装操作系统**”（不要选简易安装，否则无法在安装时勾选 OpenSSH）
- 客户机 OS：Linux → Ubuntu 64 位
- 虚拟机文件位置：`D:\softwares\Virtual Machines\<名称>\`
- 磁盘建议 40G、内存 4G、NAT 网络（默认）
- “自定义硬件”→ CD/DVD 选“使用 ISO 映像文件”→ 指向 ubuntu ISO
- ⚠️ 本人虚拟机当时磁盘只建了 10G，导致后续 K8s 阶段磁盘爆炸（见踩坑 P6）——**建议一开始就 40G**

### A.2 装系统与收尾

```bash
# 安装向导三处关键点
#   ① Profile 页：用户名 lcc、主机名、密码（记牢）
#   ② SSH Setup 页：务必勾选 Install OpenSSH server（空格勾选）
#   ③ 存储：Use entire disk → Done

# 装完登录后立刻做（顺序很重要）
sudo apt update && sudo apt upgrade -y
ip addr                      # 记下本机 IP

# 拍快照（VMware 界面操作，不是命令）
#   虚拟机右键 → 快照 → 拍摄快照，命名"纯净系统-SSH可用"
```

### A.3 SSH 远程操作（后续所有操作方式）

```powershell
# Windows PowerShell
ssh lcc@192.168.157.128
```

要点：
- VMware 程序必须常开（它是虚拟机“本体”），但日常操作全走 SSH，VMware 窗口可最小化。
- 这就是“跳板机/远程服务器”操作模式的最小复刻。

### A.4 克隆第二台节点

```
1. node1 关机（shutdown 或 VMware 里关）
2. VMware → 右键 node1 → 管理 → 克隆
3. 克隆类型：完整克隆（链接克隆依赖原机，别选）
4. 名称 ubuntu-node2，位置 D:\softwares\Virtual Machines\ubuntu-node2
5. 开机后 node2 上执行：
```

```bash
# 改主机名（区分双胞胎）
sudo hostnamectl set-hostname lccserver-node2
# 重新登录生效

# （进阶但规范）换掉克隆来的 SSH 主机密钥
sudo rm /etc/ssh/ssh_host_*
sudo dpkg-reconfigure openssh-server
```

⚠️ 克隆后 `docker ps` 可能是空的——不是容器丢了，而是它们处于停止态。`docker ps` 只显示运行中的容器，用 `docker ps -a` 看全部，`docker start xxx` 拉起。

### A.5 密钥免密登录（node1 → node2）

```bash
# node1 上
ssh-keygen -t ed25519 -C "lcc@node1"
#   三处交互全回车（不要设 passphrase，否则每次用要输）
ssh-copy-id lcc@<node2-IP>      # 输最后一次密码
ssh lcc@<node2-IP>              # 免密验证
```

```powershell
# Windows 经 node1 跳 node2（跳板机姿势）
ssh -J lcc@<node1-IP> lcc@<node2-IP>
```

**排查“配了密钥还要密码”**：① 公钥真发过去了吗（对比两端 pub 内容）② node2 上 `chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys` ③ 连错机器（看 hostname）④ 服务端日志 `sudo tail -f /var/log/auth.log`。

---

## 三、阶段 B：容器基础（在虚拟机里练 Docker）

### B.1 装 Docker（Ubuntu 源方案，学习够用）

```bash
sudo apt install -y docker.io docker-compose-v2
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker lcc     # 免 sudo（需重新登录生效）
exit                            # 重连验证
docker run hello-world          # 首次验证
```

### B.2 国内网络：拉镜像必配加速

虚拟机 Docker（`/etc/docker/daemon.json`）：
```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io", "https://dockerproxy.net"]
}
EOF
sudo systemctl restart docker
```

> 三种“加速配置”位置对照（都干同一件事）：Linux Docker=`/etc/docker/daemon.json`、Docker Desktop=Settings→Docker Engine GUI、K3s=`/etc/rancher/k3s/registries.yaml`。

### B.3 四个练手实验（对照学习笔记做）

```bash
# 实验1：端口映射 + Nginx
docker run -d --name my-nginx -p 8080:80 nginx:latest
curl http://localhost:8080     # 出 Welcome to nginx

# 实验2：进容器（容器=迷你Linux）
docker exec -it my-nginx bash
#   cat /etc/os-release / hostname / ls /usr/share/nginx/html  → exit

# 实验3：MySQL + Volume 挂载 + 环境变量
mkdir -p ~/docker-data/mysql
docker run -d --name my-mysql \
  -p 3306:3306 \
  -v /home/lcc/docker-data/mysql:/var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD=123456 \
  mysql:8.0
docker logs -f my-mysql        # 看到 "ready for connections" 后 Ctrl+C
docker exec -it my-mysql mysql -uroot -p123456
#   CREATE DATABASE testdb; USE testdb;
#   CREATE TABLE student(id INT PRIMARY KEY, name VARCHAR(50));
#   INSERT INTO student VALUES(1,'lcc'); SELECT * FROM student; exit;

# 实验4：删容器数据复活（验证挂载意义）
docker rm -f my-mysql
ls ~/docker-data/mysql          # 宿主机数据文件还在
docker run -d --name my-mysql2 -p 3306:3306 \
  -v /home/lcc/docker-data/mysql:/var/lib/mysql -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0
docker exec -it my-mysql2 mysql -uroot -p123456
#   USE testdb; SELECT * FROM student;   # 数据还在 = 挂载生效
```

### B.4 docker run 参数速查（务必吃透）

| 参数 | 全称 | 作用 | 类比 |
|------|------|------|------|
| `-d` | detached | 后台运行 | 关门让它自己跑 |
| `--name` | — | 容器命名（全局唯一） | 贴标签 |
| `-p 宿:容` | publish | 端口映射（外面端口:容器端口） | 墙上开孔接管子 |
| `-v 宿:容` | volume | 目录挂载（宿主机路径:容器内路径） | 通往仓库的门（容器删数据不丢） |
| `-e KEY=V` | env | 环境变量注入 | 递给程序的配置单 |
| `-it` | interactive+tty | 交互式终端（进容器用） | 拿个能敲字的屏幕 |
| `--restart=always` | — | 开机/退出自动重启 | 服务自愈开关 |
| `--rm` | remove | 容器退出即自动删除（临时实验用） | 用完即扔 |
| `-f` | follow（logs）| 持续跟踪输出 | tail -f 效果，Ctrl+C 退出 |
| `--memory/--cpus` | — | 限制容器资源（企业必配，防挤爆宿主机） | 给容器划限额 |
| `--network` | — | 指定网络（默认 bridge） | 决定容器怎么上网 |

### B.5 docker run 逐参数拆解（以实验3 MySQL 为例）

```bash
docker run -d --name my-mysql \          # 反斜杠 = 命令续行，下一行仍是同一命令
  -p 3306:3306 \                         # 宿主机3306 → 容器3306
  -v /home/lcc/docker-data/mysql:/var/lib/mysql \   # 宿主机目录:/容器内目录
  -e MYSQL_ROOT_PASSWORD=123456 \        # 传给容器的配置，MySQL 镜像首次初始化读它做root密码
  mysql:8.0                              # 镜像名:版本tag；本地没有会自动去仓库拉
```

拆解这套命令的执行顺序（运行时的幕后流程）：

```
1. 检查本地有无 mysql:8.0 → 没有则从加速源拉取
2. 基于镜像创建容器（独立文件系统/网络/进程空间）
3. 配置端口映射、挂载目录、注入环境变量
4. 执行镜像里定义的启动命令（mysqld）→ 容器进入运行
5. 返回容器 ID（-d 让它在后台跑）
```

> 运行期验证参数变成配置：`docker inspect my-mysql`，在输出里找 `HostConfig.PortBindings`（端口映射）、`Mounts`（挂载）、`Config.Env`（环境变量）——参数和容器配置一一对应。

### B.6 Volume 挂载方案详解（三种形式，务必分清）

**为什么需要挂载**：容器里的文件写在容器自己的临时层，容器一删就没了。Volume 挂载 = 在容器内目录和宿主机目录之间开一扇"双向门"，数据落到宿主机上。

**三种形式对照**：

| 形式 | 写法 | 宿主机位置 | 典型场景 | 目录不存在时 |
|------|------|-----------|---------|-------------|
| **① 目录挂载（bind mount）** | `-v /宿主机绝对路径:/容器路径` | 你自己指定的路径 | 数据库数据、日志目录 | Docker 自动创建，但**属主是 root** |
| **② 命名卷（named volume）** | `-v 卷名:/容器路径` | Docker 统一管理（`/var/lib/docker/volumes/<卷名>/`） | 企业常用；备份迁移方便 | Docker 自动创建卷，无属主坑 |
| **③ 匿名卷** | `-v /容器路径` | Docker 随机分配 | 只声明"要持久"不关心放哪 | 少用，不好管理 |

**逐条示例**：

```bash
# ① 目录挂载（你实验里用的，最直观）
docker run -d -v /home/lcc/mysql-data:/var/lib/mysql mysql:8.0

# ② 命名卷（-v 卷名，不是路径！）
docker volume create mysql-data        # 先建卷（可选，run 时自动建）
docker run -d -v mysql-data:/var/lib/mysql mysql:8.0
docker volume ls                       # 看所有卷
docker volume inspect mysql-data       # 看卷真实路径
docker volume rm mysql-data            # 删卷（容器删了卷还在，卷要单独删）

# ③ 匿名卷（不推荐学习期使用）
docker run -d -v /var/lib/mysql mysql:8.0
```

**三个特例/坑（真实会遇到，必须知道）**：

```bash
# 坑1：宿主机目录不存在时 Docker 自动创建，但属主是 root
#   表现：容器能写（容器内常是root），但你在宿主机直接操作该目录要 sudo
#   预防：先 mkdir -p 并指定属主
mkdir -p /home/lcc/mysql-data && sudo chown -R 1000:1000 /home/lcc/mysql-data
#   （MySQL 容器内 mysql 用户 uid 是 999/1000，chown 到对应 uid 最稳，踩过才懂）

# 坑2：把【空目录】挂到容器内【本有默认内容】的路径，会把容器默认内容"盖住"
#   表现：容器能启动但行为异常（比如挂空目录到 /etc/nginx/conf.d 丢默认配置）
#   解法：先 docker cp 把默认配置考出来再挂（配置类服务的标准套路）
docker cp my-nginx:/etc/nginx/nginx.conf ./nginx.conf
docker run -d -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro nginx:latest
#   （:ro = read-only 只读挂载，配置在容器里只许读，防误改，企业习惯）

# 坑3：同名目录重复挂载 = 数据"沿用"而非"覆盖"
#   这就是实验4"删容器数据复活"的原理：删容器 → 重跑带同一 -v → MySQL 看到旧数据直接用
```

**与 docker-compose（以后会学到）的对照**：

```yaml
# docker-compose.yml 里 volumes 段落写法
services:
  mysql:
    image: mysql:8.0
    volumes:
      - mysql-data:/var/lib/mysql    # 命名卷（推荐）
      - ./conf:/etc/mysql/conf.d:ro   # 目录挂载（相对路径也可）
volumes:
  mysql-data:          # 顶层声明卷（相当于 docker volume create）
```

---

## 四、阶段 C：Nacos 部署（Docker 方式）

```bash
docker run -d --name my-nacos \
  -p 8848:8848 -p 9848:9848 \
  -v /home/lcc/docker-data/nacos:/home/nacos/data \
  -e MODE=standalone \
  nacos/nacos-server:v2.3.2
docker logs -f my-nacos     # 看到 "Nacos started successfully" 后 Ctrl+C
```

- 控制台：`http://<虚拟机IP>:8848/nacos`
- ⚠️ **Nacos 2.2+ 默认不开启鉴权**，所以控制台不要求登录——这是默认策略，不代表安全。
- 企业级开启方式（学习可跳过）：加环境变量 `NACOS_AUTH_ENABLE=true`、`NACOS_AUTH_TOKEN`（≥32字节）、`NACOS_AUTH_IDENTITY_KEY/VALUE`。
- 最小验证：控制台 → 配置管理 → 发布一条 YAML 配置（Data ID 如 `demo-config.yaml`）。

---

## 五、阶段 D：Docker Desktop（Windows 开发机）+ 项目镜像

### D.1 为什么装

不是替代虚拟机，而是补“开发迭代快”的短板：虚拟机 Docker 是运维视角（服务器部署），Docker Desktop 是开发视角（改代码 10 秒反馈、localhost 直连）。**企业里两者并存**：开发本机构建，服务器/CI 运行。

### D.2 WSL2 安装（Docker Desktop 的前置）

踩坑：`wsl --install` 报 403（微软商店被墙）。解法：

```powershell
# 管理员 PowerShell：手动启用两个系统功能
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
# 重启

# 然后 GitHub 下载 WSL 包并安装（选 x64.msi，约 344MB，别下 ARM64/全家桶）
# https://github.com/microsoft/WSL/releases  →  wsl.2.9.9.0.x64.msi 双击装
wsl --version    # 验证
```

**WSL2 是什么**（一句话）：微软内置的轻量级 Linux 虚拟机平台。Docker Desktop 站在它上面跑容器；VMware Ubuntu 是它的“重量级兄弟”。两者共存不冲突。

### D.3 Docker Desktop 安装到 D 盘

图形安装器不能选路径 → 用命令行（**必须管理员 PowerShell**，`--installation-dir` 属于机器级安装）：

```powershell
& ".\Docker Desktop Installer.exe" install --quiet --accept-license `
  --installation-dir=D:\softwares\Docker `
  --wsl-default-data-root=D:\softwares\Docker\wsl-data
```

- 装完 C 盘零占用（程序 3.3GB + 虚拟磁盘全在 D）。
- 跳过登录没影响（本地 build/run 不需要，push 才要）。
- ⚠️ 旧终端 `docker` 命令找不到 = PATH 未刷新，重开终端即可；临时用全路径 `D:\softwares\Docker\resources\bin\docker.exe`。
- ⚠️ **Docker Desktop 不读 `~/.docker/daemon.json`**（它的引擎配置由 GUI 管理），配镜像加速要在 Dockerfile 里改 FROM，或 Settings→Docker Engine 里加 `registry-mirrors`。

### D.4 项目多阶段 Dockerfile（backend/Dockerfile）

```dockerfile
# 多阶段：编译与运行分离（学习笔记第十一节）
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
COPY docker/maven-settings.xml /root/.m2/settings.xml   # 阿里云 Maven 源
RUN mvn dependency:go-offline -B                        # 分层缓存：pom 先行
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine
ENV TZ=Asia/Shanghai
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser                      # 非 root 运行（安全规范）
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=dev JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

配套 `backend/docker/maven-settings.xml`（阿里云 Maven 镜像，加速依赖下载）。

```powershell
# 构建 & 运行（Windows）
cd d:\project\lcc-github\ai-agent-explorer\backend
docker build -t research-agent:1.0 .
docker run -d --name research-agent -p 8080:8080 research-agent:1.0
curl http://localhost:8080/api/tasks       # 200 [] = 通
```

### D.5 交付：镜像部署到服务器（手工 CI/CD）

```powershell
docker save -o research-agent-1.0.tar research-agent:1.0
scp .\research-agent-1.0.tar lcc@<node1-IP>:/home/lcc/
```

```bash
# node1
docker load -i research-agent-1.0.tar
docker run -d --name research-agent -p 8081:8080 --restart=always research-agent:1.0
```

Windows 访问 `http://<node1-IP>:8081/api/tasks` → 200 = **“本机构建 → 服务器部署”链路打通**。

⚠️ 大坑教训：`docker save` 产生的 tar 曾因**本地文件本身截断**而无法 load/import——症状是导入报 `short read: expected X bytes but got Y`，且两端 `sha256sum` 一致（截断文件哈希也是稳定的！）。**判断文件好坏必须做本地 `docker load -i` 验证**，哈希一致≠文件好。

---

## 六、阶段 E：K3s / K8s 编排（装在 node2）

### E.1 安装 K3s（单节点集群）

```bash
curl -sfL https://rancher-mirror.rancher.cn/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn INSTALL_K3S_KUBECONFIG_MODE="644" sh -
# 注意：整条一行粘贴；拆成多行环境变量会被当成命令报 "command not found"
```

验证与权限修复：

```bash
sudo k3s kubectl get nodes        # Ready 即成功

# 永久修复 kubectl 权限（K3s 重启会把 /etc/rancher/k3s/k3s.yaml 重置回 600）
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown lcc:lcc ~/.kube/config
sed -i '/KUBECONFIG/d' ~/.bashrc    # 删掉指向 /etc 的 export（它会被重置权限）
unset KUBECONFIG
kubectl get nodes
```

### E.2 国内加速 + 镜像导入（K3s 用 containerd，不是 Docker！）

```bash
# K3s 拉 pause 沙箱镜像被墙 → 配加速
sudo tee /etc/rancher/k3s/registries.yaml <<'EOF'
mirrors:
  docker.io:
    endpoint:
      - "https://docker.m.daocloud.io"
EOF
sudo systemctl restart k3s        # 重启后记得重做 ~/.kube/config（权限被重置）

# 导入项目镜像：必须带 -n k8s.io（kubelet 只看这个命名空间！）
sudo k3s ctr -n k8s.io images import /tmp/research-agent-1.0.tar
sudo k3s ctr -n k8s.io images ls | grep research-agent
```

### E.3 Deployment + Service YAML（~/k8s-lab/research-agent.yaml）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: research-agent
spec:
  replicas: 2
  selector:
    matchLabels:
      app: research-agent
  template:
    metadata:
      labels:
        app: research-agent
    spec:
      containers:
        - name: research-agent
          image: research-agent:1.1
          ports:
            - containerPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: research-agent-svc
spec:
  type: NodePort
  selector:
    app: research-agent
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30081
```

```bash
kubectl apply -f ~/k8s-lab/research-agent.yaml
kubectl get pods -w        # 2 个 1/1 Running
kubectl get svc research-agent-svc
curl -s http://localhost:30081/api/tasks
# Windows: curl http://<node2-IP>:30081/api/tasks   → 200 []
```

### E.4 四大能力实战（全部亲测通过）

```bash
# ① 扩容
kubectl scale deployment research-agent --replicas=3
kubectl get pods

# ② 自愈（声明式：说 2 就永远 2，谁挂补谁）
kubectl delete pod <pod名>
kubectl get pods -w        # 新 Pod 自动补上

# ③ 滚动更新（零停机发版）
kubectl set image deployment/research-agent research-agent=research-agent:1.1
kubectl rollout status deployment/research-agent
# 另开窗口：while true; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:30081/api/tasks; sleep 1; done

# ④ 回滚
kubectl rollout undo deployment/research-agent
kubectl rollout undo deployment/research-agent --to-revision=4
```

版本确认三件套：
```bash
kubectl get deployment research-agent -o jsonpath='{.spec.template.spec.containers[0].image}'
kubectl logs deployment/research-agent | grep "version 1.1"   # 1.1 有标记日志，1.0 没有
curl -s http://localhost:30081/api/tasks
```

### E.5 kubectl 运维命令速查

```bash
kubectl get nodes / pods / svc / deployment / endpoints
kubectl describe pod <pod>            # 排障第一入口，Events 写原因
kubectl logs -l app=research-agent    # 按标签取日志（logs deployment 只随机挑一个 Pod）
kubectl rollout history deployment    # 版本历史
kubectl taint nodes <node> <key>:NoSchedule-   # 结尾减号=删污点
kubectl explain deployment.spec...    # 万能字段查询
```

---

## 七、踩坑大全（血泪经验，务必先读）

| # | 坑 | 现象 | 根因 | 解法 |
|---|-----|------|------|------|
| P1 | 克隆后 docker ps 空 | 以为容器丢了 | docker ps 只显示运行中 | 用 docker ps -a，docker start |
| P2 | 多行命令粘贴丢参数 | 数据库数据“丢失” | 换行粘贴时 -v 掉了 | 整行粘贴；起有状态服务后 docker inspect 查 Mounts |
| P3 | wsl --install 403 | 微软商店被墙 | 国内网络 | dism 开功能 + GitHub 下 msix/msi |
| P4 | Docker Desktop 不读 daemon.json | registry-mirrors 无效 | Desktop 引擎配置由 GUI 管 | FROM 直接写加速源域名 / GUI 里配 |
| P5 | --installation-dir 装不上 | 静默失败 | 需管理员权限 | 管理员 PowerShell 执行 |
| P6 | 磁盘 10G 塞满 → Pod 驱逐风暴 | 一堆 Evicted + Pending | LVM 根分区太小 + ephemeral-storage 压力 | VMware 扩盘 + growpart/pvresize/lvextend/resize2fs 三连（见下） |
| P7 | 磁盘扩容“没用” | VMware 扩了 df 还是 9.8G | 只扩了虚拟盘，没扩分区/LVM | 系统内跑 LVM 三连 |
| P8 | 磁盘压力污点不消失 | 资源充足仍 Pending | kubelet 检测周期长 | 手动 kubectl taint nodes ...- 摘除 |
| P9 | K3s 找不到镜像 | ImagePullBackOff / ErrImagePull | 镜像没进 k8s.io 命名空间 / 被 GC | 导入带 -n k8s.io；ctr images ls 确认 |
| P10 | containerd 镜像“消失” | k8s.io 全空 | GC 回收无 Pod 引用的镜像 | 重新导入 |
| P11 | tar 导不进 | short read 报错 | docker save 的文件本身截断（哈希一致≠文件好） | 本地 docker load 验证 → 重新 save |
| P12 | kubectl 权限反复丢 | permission denied | K3s 重启重置 k3s.yaml 权限 | ~/.kube/config 副本方案（见 E.1） |
| P13 | apply 与 set image 混用警告 | last-applied-configuration 注解失效 | 声明式 vs 命令式混用 | 一个资源只用一种方式管理 |
| P14 | rollout undo --to-revision 找不到 | unable to find revision | history 只保留近版本 | 先 rollout history 查号 |
| P15 | K3s 的 k3s.yaml 重启重置 | chmod 644 白做 | 服务重启重置权限 | 永久方案见 E.1 |

### LVM 磁盘扩容三连（P6/P7 解法，企业高频操作）

```
前提：VMware 虚拟机关机 → 设置 → 硬盘 → 扩展 → 改大 → 开机
```

```bash
lsblk                                          # 确认 sda 物理卷分区号（一般 sda3）
sudo growpart /dev/sda 3                       # ① 扩分区
sudo pvresize /dev/sda3                        # ② 扩物理卷
sudo lvextend -l +100%FREE /dev/ubuntu-vg/ubuntu-lv   # ③ 扩逻辑卷
sudo resize2fs /dev/mapper/ubuntu--vg-ubuntu--lv       # ④ 扩文件系统
df -h                                          # 验证：根分区应变大
```

---

## 八、关键概念速查（脱离会话也能复习）

| 概念 | 一句话解释 |
|------|-----------|
| ISO | 光盘的数字复制版文件，虚拟机用它当“安装光盘” |
| 快照 | 虚拟机状态存档点，搞坏了秒级回滚（VMware 专属优势） |
| 完整克隆 vs 链接克隆 | 完整=独立副本；链接=依赖原机，删原机就废 |
| sudo / 免 sudo | 前者临时借 root；后者把你加进 docker 组，一劳永逸 |
| Volume 挂载 | 容器内目录 ↔ 宿主机目录开共享门；容器删数据不丢 |
| 镜像/容器/仓库 | 模板 / 运行实例 / 下载源 |
| 多阶段构建 | 编译用大镜像，运行用精简 JRE，产物只留 jar |
| 分层缓存 | pom 先行下载依赖，改代码不重下依赖 |
| WSL2 | 微软内置轻量 Linux 虚拟机；Docker Desktop 的底座 |
| NAT/桥接/私有 IP | NAT 下 VM 只能被宿主机访问；192.168.x.x 是私有地址 |
| 跳板机 | 内网唯一入口，全连接有日志可审计；本质=管控+审计，不是连不上 |
| 密钥登录 | 公钥(锁)装服务器，私钥(钥匙)在你手里，免密不传密码 |
| Pod / Deployment | Pod=最小部署单元；Deployment=声明要几个副本并自愈补齐 |
| Service(NodePort) | 给 Pod 固定门牌号，按 label 转发，Pod 换 IP 无感 |
| 滚动更新 | 新副本 Ready 才杀旧副本，全程不中断 |
| 声明式 vs 命令式 | apply(yaml)=声明期望态；set image/rollout=直接改；别混用 |

---

## 九、当前环境状态清单（截至 2026-09-08）

| 项 | 状态 |
|----|------|
| Windows Docker Desktop | 已装 D:\softwares\Docker，WSL2 底座，本地镜像 1.0/1.1 |
| node1（lccserver） | Docker 全家桶 + research-agent 容器(8081)，磁盘**待扩容**（10G 小盘） |
| node2（lccserver-node2） | Docker + K3s v1.36 + research-agent **1.1** Deployment(2副本, NodePort 30081) |
| node2 磁盘/内存 | 根分区 38G（已扩容），内存 6G |
| 代码改动 | `ResearchAgentApplication.java` main 加了 “Starting version 1.1” 标记日志 |
| 后端 Dockerfile | `backend/Dockerfile`（多阶段 + 加速源 + 非 root）已就绪 |
| K8s 服务 | curl 30081 正常，镜像在 k8s.io 命名空间 |

---

## 十、未完成 / 待办学习项（下次从这里续）

- [ ] **拍快照存档**（强烈建议先做）：VMware → node2 → 快照“K3s-1.1稳定版”；node1 → “Docker全家桶就绪”
- [ ] **node1 磁盘扩容**：同 node2 的 LVM 三连（P6/P7 解法），防患未然
- [ ] **Docker Desktop 长期加速**：Settings → Docker Engine 里补 `registry-mirrors`（写死 FROM 只是临时方案）
- [ ] **双节点 K3s 集群**：把 node1 以 agent 身份加入 node2 的 K3s（K3S_URL + K3S_TOKEN），体验真实多节点调度
- [ ] **Ingress**：把 NodePort 换成 Ingress 统一入口（对应学习笔记 Ingress=大门）
- [ ] **CI/CD 概念落地**：手工 save/scp/load 是流水线原始版；进阶可用 GitLab CI / GitHub Actions 自动化
- [ ] **给前端打镜像**：本项目 frontend（Next.js）可复用同一套多阶段 + Dockerfile 思路
- [ ] **K8s YAML 深化**：readinessProbe/livenessProbe（让滚动更新更智能）、ConfigMap/Secret（配置解耦）、资源 requests/limits（防止 Pod 挤爆节点）
- [ ] **Nacos 鉴权企业级配置**：NACOS_AUTH_ENABLE=true 全家桶（默认不鉴权的对照）

---

## 十一、推荐学习顺序回顾

```
阶段1 本机：MySQL + Spring Boot + Nginx（项目已跑通，跳过）
阶段2 Docker：本文件 阶段B/C/D（已通）
阶段3 K8s：本文件 阶段E（已通），继续上面"待办"深化
```

> 判断自己“真会了”的标准：不看文档，能把 阶段D.4 的 Dockerfile 和 阶段E.3 的 YAML 默写出来，并解释每个字段为什么存在。

---

# 附录：命令详解大全（`-x` 逐个讲）

> 下面的命令都拆到"每个参数什么意思"，作为学习期的"字典"，不用背，遇到不会的回来查。

## 十二、Linux 常用命令

### 12.1 目录与文件（最常用）

```bash
pwd                    # 当前在哪（print working directory）
ls                     # 列出当前目录文件
ls -l                  # -l long：详细列表（权限/属主/大小/时间）
ls -a                  # -a all：显示隐藏文件（.开头的）
ls -la                 # 组合拳：常用
ls -lh                 # -h human：大小显示成 K/M/G（人性化）
cd <目录>               # 进入目录（change directory）
cd ..                  # 上一级目录（.. = 父目录）
cd ~                   # 回自己家目录（~ = /home/lcc）
cd -                   # 回到上一个待过的目录
mkdir <名字>            # 建目录（make directory）
mkdir -p a/b/c         # -p parents：连父目录一起建（不存在不报错）
cp <源> <目标>          # 复制
cp -r <目录> <目标>     # -r recursive：复制目录必须带
mv <源> <目标>          # 移动/重命名（mv 旧名 新名 = 改名）
rm <文件>              # 删除文件
rm -r <目录>           # -r：删目录
rm -f <文件>           # -f force：不提示强删（危险）
rm -rf <目录>          # ⚠️ 组合拳，删除一切，慎用！rm -rf / 会删系统
```

### 12.2 查看文件内容

```bash
cat <文件>             # 整个文件打印到屏幕
head -n 20 <文件>      # 看前20行（默认10行）
tail -n 30 <文件>      # 看最后30行（查日志首选）
tail -f <日志文件>      # -f follow：实时跟踪新增内容（Ctrl+C 退出）——docker logs -f 同款思想
less <文件>            # 分页浏览（q 退出，/ 搜索）
wc -l <文件>           # 数行数
grep "关键字" <文件>    # 过滤含关键字的行（日志排查神器）
grep -i "xxx"          # -i ignore：忽略大小写
grep -n "xxx"          # -n：带行号
grep -A 5 "xxx"        # -A after：匹配行后5行也打印（describe pod | grep -A 10 Events 就是它）
grep -r "xxx" <目录>    # -r：递归搜目录里所有文件
grep -v "xxx"          # -v：反选，打印不匹配的行
```

### 12.3 进程与资源

```bash
ps aux                # 看所有进程（a所有终端/u以用户格式/x含无终端）
ps -ef | grep java    # 找 java 进程（结合 grep 过滤）
top                   # 实时资源监控（q 退出）
free -h               # 内存占用（-h 人性化）
df -h                 # 磁盘分区占用（-h 人性化，df=disk free）
du -sh <目录>          # 目录占用多大（-s summarize汇总 / -h）
du -sh *              # 当前目录下每个子项多大（找"谁占满了磁盘"）
uptime                # 开机多久/负载
kill <PID>            # 杀进程（PID 从 ps 拿）
kill -9 <PID>         # 强制杀（不优雅，救急用）
```

### 12.4 权限（Linux 多用户核心）

```bash
whoami                # 我是谁（当前用户）
sudo <命令>            # 以 root 执行一次（superuser do）
chmod 600 <文件>       # 改权限数字法（6=读写 0=无；4读/2写/1执行）
chmod 700 <目录>       # 7=rwx 全部
chmod 644 <文件>       # 常见文件权限：owner读写，其他只读
chown lcc:lcc <文件>   # 改属主:属组（change owner）
chown -R lcc:lcc <目录> # -R：目录下全部递归改
sudo -i / sudo su     # 切换到 root 交互终端（慎用，用完全身而退 exit）
```

> 数字权限速记：r(读)=4、w(写)=2、x(执行)=1，相加填三个位置。`chmod 750` = owner全部(7) + 组读执行(5) + 其他无(0)。SSH 报权限错基本都在调这些。

### 12.5 网络

```bash
ip addr               # 查本机 IP（新版 ip 命令，替代 ifconfig）
ip route              # 看默认网关
ping <IP或域名>        # 测连通（-c 4 = 发4次，别让它一直发）
curl http://...       # 访问网页/接口（后端自测神器）
curl -s               # -s silent：不显示进度条（脚本里常用）
curl -o /dev/null -w "%{http_code}" URL   # 只看状态码（200/404）
curl -i URL           # 带响应头看
ss -tlnp              # 看本机监听的端口（t=tcp l=listen n=数字 p=进程）替代 netstat
nc -vz <IP> <端口>     # 测端口通不通（telnet 替代）
hostname              # 主机名
```

### 12.6 系统服务与包管理

```bash
systemctl status <服务>    # 看服务状态（如 ssh/docker/k3s）
systemctl start/stop/restart <服务>   # 启/停/重启
systemctl enable <服务>    # 开机自启
systemctl disable <服务>   # 取消开机自启

apt update               # 刷新软件源索引（改了源必须做）
apt install -y <包名>     # 装包（-y = yes 不询问）
apt remove <包名>         # 卸载
apt upgrade -y            # 升级所有已装包
apt search <关键词>        # 搜软件
journalctl -u <服务>      # 看服务日志（systemd 的日志）
journalctl --vacuum-size=100M   # 清理旧日志到100M内（救磁盘用）
```

### 12.7 文件传输与归档

```bash
tar czf 包名.tar.gz <目录>      # 打包压缩（c=create z=gzip f=file）
tar xzf 包名.tar.gz            # 解包（x=extract）
tar tvf 包名.tar.gz            # 只看包内容不展开（t=list）
zip/unzip / tar -cvf          # 其他格式，用的少
sha256sum <文件>               # 算文件哈希（传输校验！）
```

---

## 十三、SSH 与文件传输命令详解

```bash
# 基础连接
ssh lcc@192.168.157.128
#   结构：ssh 用户名@主机地址
#   首次连接会问指纹 → yes；之后输密码
#   常见参数：
#     -p 2222    端口不是22时用（-p 指定端口）
#     -i 密钥文件   指定用哪个私钥
#     -v          verbose 调试，看握手过程

# 在远程执行单条命令（不进入交互，跑完就返回）
ssh lcc@192.168.157.128 hostname
ssh lcc@192.168.157.128 "ls ~ && df -h"    # 多条用引号包

# 跳板机：经 node1 跳 node2
ssh -J lcc@192.168.157.128 lcc@<node2-IP>
#   -J = ProxyJump：先连 -J 后面那台，再从它连最终目标
```

```bash
# 文件传输 scp（基于 SSH 的复制，参数含义和 cp 一致）
scp 本地文件 lcc@192.168.157.128:/home/lcc/    # 本地上传
scp lcc@192.168.157.128:/home/lcc/x.tar ./     # 远程下载
scp -r 本地目录 lcc@192.168.157.128:/home/lcc/ # -r 传目录
scp -P 2222 ...                               # 指定端口（大写P，和 ssh -p 不同！）

# 流式传输（不落中间文件，杜绝截断——绕开 P11 坑的终极方案）
docker save research-agent:1.0 | gzip | ssh lcc@192.168.157.128 "gunzip | sudo k3s ctr -n k8s.io images import -"
```

```bash
# 密钥对生成与分发
ssh-keygen -t ed25519 -C "lcc@node1"
#   -t = type 算法：ed25519（现代推荐，短且快）vs rsa（老传统 2048/4096）
#   -C = comment 备注：写你的标识，方便知道这是谁的钥
#   交互：路径回车默认；passphrase 回车空（设了则每次使用要输）
#   产物：~/.ssh/id_ed25519（私钥，绝不给别人）
#         ~/.ssh/id_ed25519.pub（公钥，发给服务器）

ssh-copy-id lcc@192.168.157.128
#   把你的公钥追加到目标机的 ~/.ssh/authorized_keys
#   原理：服务器从此"认识你的公钥"，你登录时用私钥证明自己 = 免密

# 免密失败的三大排查（P 系列经验）
cat ~/.ssh/id_ed25519.pub                    # ① 公钥存在吗
chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys   # ② 权限对吗（在目标机上）
ssh lcc@<IP> hostname                        # ③ 连的机器对吗
sudo tail -f /var/log/auth.log               # ④ 服务端日志说了啥
```

---

## 十四、Docker 命令详解（按用途分组，`-x` 逐个拆）

### 14.1 镜像（Image）

```bash
docker images                    # 看本地镜像列表（含大小/创建时间）
docker search <名字>             # 搜 Docker Hub 上的镜像
docker pull <镜像名:tag>         # 只拉不跑
docker pull mysql:8.0            # 具体化：不写 tag 默认 latest（生产要写死版本！）
docker build -t 名字:版本 .      # 用当前目录 Dockerfile 构建
#   -t = tag：给镜像起名 名字:tag
#   .  = 构建上下文路径（把哪个目录发给 Docker daemon 做原料）
docker build -f Dockerfile.dev -t xx:1.0 .   # -f 指定别的 Dockerfile 文件名

# 镜像迁移（P11 主角）
docker save -o research-agent-1.0.tar research-agent:1.0
#   -o = output：导出到文件
docker load -i research-agent-1.0.tar
#   -i = input：从文件导入
docker rmi <镜像名>              # 删镜像（remove image）
docker image prune -f           # 删悬空镜像（<none> 那种，-f 不询问）
docker tag 旧名:旧tag 新名:新tag  # 改镜像标签
```

### 14.2 容器（Container）

```bash
docker run <镜像>                # 创建并启动
docker run -it ubuntu bash      # 交互进容器
docker run -d --name web -p 80:80 nginx   # 后台+命名+端口（实战模板）
docker ps                       # 运行中的容器
docker ps -a                    # 所有容器（含 Exited；-a = all）
docker ps -aq                   # 只打印容器ID（配 xargs 批量删用）
docker start/stop/restart <容器> # 启/停/重启（start 作用于已存在容器）
docker rm <容器>                # 删容器（先 stop）
docker rm -f <容器>             # 强制删（运行中也删）
docker rm $(docker ps -aq)      # 删掉所有容器（清理用）

# 交互与日志
docker exec -it <容器> bash     # 进容器（-i交互 -t终端；bash没有的镜像用 sh）
docker exec -it <容器> mysql -uroot -p123456   # 直接执行容器内程序
docker logs <容器>              # 看全部日志
docker logs -f <容器>           # 实时跟踪（-f follow，Ctrl+C退出）
docker logs --tail 100 <容器>   # 只看最后100行
docker logs -f --tail 50 名字   # 组合：从末尾50行开始实时跟

# 信息查看
docker inspect <容器>           # 看容器全部配置（Mounts/PortBindings/Env...）
docker inspect --format '{{.HostConfig.RestartPolicy.Name}}' <容器>   # 格式化取单字段
docker port <容器>              # 看端口映射
docker stats                    # 实时资源占用（Ctrl+C退出）
docker top <容器>               # 看容器内进程
docker diff <容器>              # 容器和镜像的差异（改了哪些文件）
```

### 14.3 管理（开机自启 / 清理）

```bash
docker update --restart=always <容器>   # 给已运行容器加开机自启
#   --restart 可选值：no(默认) / always(挂了就重启，企业标配)
#                    / on-failure:3(仅失败重启，最多3次) / unless-stopped(除非手动停)
docker system df                # 看空间都被谁占了
docker system prune             # 清理（容器停止的/网络/悬空镜像，交互确认）
docker system prune -a          # 更狠：连没用到的镜像也删（-a all）
docker builder prune            # 只清构建缓存
docker volume ls / volume rm    # 管理卷（见 B.6）
```

### 14.4 一个完整的"上生产"命令串（汇总）

```bash
# 构建 → 导出 → 传服务器 → 导入 → 带自启运行 → 验证
docker build -t my-app:1.0 .                     # ① 本机构建
docker save -o my-app.tar my-app:1.0             # ② 导出
scp my-app.tar lcc@<服务器IP>:/tmp/               # ③ 传输
docker load -i /tmp/my-app.tar                   # ④ 服务器导入
docker run -d --name my-app -p 8080:8080 \
  --restart=always --memory=1g my-app:1.0        # ⑤ 限资源+自启运行
curl -s http://localhost:8080/api/health          # ⑥ 健康检查
```

---

## 十五、kubectl 命令详解（K8s 日常，`-x` 逐个拆）

### 15.1 查询（get / describe / logs）

```bash
kubectl get nodes                    # 集群节点
kubectl get pods                     # 默认命名空间的 Pod
kubectl get pods -n kube-system      # -n namespace：指定命名空间（kube-system 是 K8s 自己的）
kubectl get pods -A                  # -A all namespaces：所有命名空间
kubectl get pods -o wide             # -o output=wide：多列详细（含 Pod 的 IP、所在节点）
kubectl get pods -w                  # -w watch：持续刷新观察（Ctrl+C 退出）
kubectl get pods --show-labels       # 看 Pod 的标签（排查 selector 用）
kubectl get pods -l app=research-agent   # -l label：按标签筛选
kubectl get all                      # 当前命名空间几乎全部资源
kubectl get svc / endpoints / deployment / rs   # 其他资源
kubectl get <资源> -o yaml           # 资源当前完整配置（YAML 输出）
kubectl get <资源> -o jsonpath='{.spec.template.spec.containers[0].image}'
#   -o jsonpath：只取某个字段（写脚本/判断用）

kubectl describe pod <名字>          # 详细信息+事件（排障第一命令）
kubectl describe pod <名字> | grep -A 10 Events   # 只看事件（-A 10=后面10行）
kubectl describe node                # 节点状态/污点/压力条件
kubectl describe svc <名字>          # Service 的 selector/端口

kubectl logs <pod>                   # Pod 日志
kubectl logs -l app=xxx              # 按标签取全部 Pod 日志（logs deployment 只随机挑1个）
kubectl logs -f <pod>                # 实时跟踪
kubectl logs --tail=50 <pod>         # 末尾50行
kubectl logs <pod> <容器名>           # 多容器 Pod 指定容器
```

### 15.2 部署操作（apply / delete / scale / exec）

```bash
kubectl apply -f 文件.yaml           # 声明式创建/更新（幂等，可反复执行）
kubectl apply -f 目录/               # 应用目录下所有 yaml
kubectl delete -f 文件.yaml          # 按文件删（连带删除它创建的资源）
kubectl delete deployment <名字>      # 删 Deployment（级联删它管的 Pod）
kubectl delete pod <名字>            # 删单个 Pod（Deployment 会自动重建=自愈演示）
kubectl delete pod --all             # 当前命名空间全删
kubectl scale deployment <名字> --replicas=3   # 改副本数（扩容/缩容）
kubectl exec -it <pod> -- bash       # 进 Pod 终端（-- 后面是容器内命令）
kubectl exec <pod> -- curl localhost:8080   # 在 Pod 内执行单条命令
```

### 15.3 发布与回滚（set / rollout）

```bash
kubectl set image deployment/xxx 容器名=新镜像   # 换镜像=触发滚动更新
kubectl rollout status deployment/xxx   # 看滚动进度（等完成）
kubectl rollout history deployment/xxx  # 版本历史（revision 列表）
kubectl rollout undo deployment/xxx     # 回滚到上一个版本
kubectl rollout undo deployment/xxx --to-revision=4   # 回滚到指定 revision
kubectl rollout restart deployment/xxx  # 原地重启（换不了代码，只重拉容器）
```

### 15.4 节点与污点

```bash
kubectl get nodes -o wide
kubectl describe node <名字> | grep -A 4 Conditions   # 看压力条件
kubectl get node -o custom-columns=NAME:.metadata.name,TAINTS:.spec.taints  # 看污点
kubectl taint nodes <node> node.kubernetes.io/disk-pressure:NoSchedule-      # 删污点（结尾-）
#   污点三大类（遇到再查即可）：
#   NoSchedule=不让新Pod上来  NoExecute=连在跑的也赶走  PreferNoSchedule=尽量不调度
```

### 15.5 学习/自查工具

```bash
kubectl explain deployment.spec.replicas   # 官方字段解释（写 YAML 万能词典）
kubectl explain pod.spec.containers
kubectl api-resources | grep deploy        # 查资源类型的 apiVersion/KIND
kubectl config current-context             # 当前用的哪个集群（多集群时确认）
```

### 15.6 K3s 特有的几个命令（不是标准 kubectl）

```bash
kubectl  =  k3s kubectl        # K3s 自带 kubectl 的等价写法
sudo k3s kubectl get nodes     # 权限没配好时用 sudo 前缀
sudo k3s ctr -n k8s.io images ls       # 看 containerd(k8s.io命名空间)里的镜像
sudo k3s ctr -n k8s.io images import 镜像.tar   # 导入镜像（kubelet 只看 k8s.io！）
sudo cat /var/lib/rancher/k3s/server/node-token   # 拿集群 token（节点加入用）
```

> 一个贯穿所有命令的记忆法：`get`=查询、`describe`=详情、`logs`=日志、`exec`=进入执行、`apply -f`=按文件部署、`delete`=删除。剩下 `-o`(输出格式)、`-n`(命名空间)、`-w`(watch)、`-f`(文件)、`-l`(标签)、`-A`(全部空间) 是高频通用 flag。

---

## 十六、YAML / Dockerfile 逐行精读

> 前文给了"能跑的代码"，这一章给"每行在干什么"。三种配置文件各挑一份代表逐行拆。
> 阅读技巧：先扫"代码列"混个眼熟，再看"说明列"，最后合起来默写。

### 16.1 Dockerfile 逐行精读（backend/Dockerfile 实际文件）

```dockerfile
# 行号只是为了下面讲解对位，Dockerfile 注释行以 # 开头
```

| 行 | 代码 | 说明 |
|----|------|------|
| 7-9 | `# ---------- 第1阶段：编译 ----------` | 注释（多阶段文件的段落分隔） |
| 10 | `FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 AS builder` | **第1阶段起点**：拉一个自带 Maven3.9+JDK21 的编译镜像；`docker.m.daocloud.io`=走国内加速源；`AS builder` 给本阶段起名，后面 COPY --from 要用 |
| 13 | `WORKDIR /build` | 切换工作目录为 `/build`（后续 COPY/RUN 都在这里，不存在会自动建） |
| 17 | `COPY pom.xml ./` | 只复制 pom.xml（不复制源码）→ 让下一步下载依赖时【源码变更不影响缓存】 |
| 18 | `COPY docker/maven-settings.xml /root/.m2/settings.xml` | 把阿里云 Maven 镜像配置放到容器 root 的 maven 配置位置 → 下载依赖走国内源 |
| 19 | `RUN mvn dependency:go-offline -B` | 先下载全部依赖并离线缓存（`-B` batch 安静模式不啰嗦）→ 这层是"依赖层"，代码改了这层不重建 |
| 22 | `COPY src ./src` | **源码才进场**：源码一变，只有这层及之后重建，前面依赖层命中缓存 |
| 23 | `RUN mvn clean package -DskipTests -B` | 编译打包出 `target/*.jar`（`-DskipTests` 跳过单测，构建不卡测试） |
| 26 | `# ---------- 第2阶段：运行 ----------` | 注释分隔 |
| 27 | `FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine` | **第2阶段起点**（新 FROM=新镜像）：只带 JRE21 的 alpine 精简系统（几十MB），**编译工具链(Maven/JDK)不进来** → 最终镜像小 |
| 30 | `ENV TZ=Asia/Shanghai` | 设时区：容器日志时间与本地一致（不设默认 UTC，日志差 8 小时） |
| 33 | `RUN addgroup -S appgroup && adduser -S appuser -G appgroup` | 建非 root 用户/组（`-S` system 系统级账号） |
| 36 | `WORKDIR /app` | 运行阶段工作目录 |
| 37 | `COPY --from=builder /build/target/*.jar app.jar` | **跨阶段复制**：把第1阶段产出的 jar 拷进运行镜像 → 只带成品不带工具链 |
| 38 | `RUN chown -R appuser:appgroup /app` | jar 归属 app 用户（否则非 root 用户没权限读） |
| 41 | `USER appuser` | **切换非 root 运行**（安全规范：容器被打穿也是低权限） |
| 44 | `EXPOSE 8080` | 只是"说明书"声明应用监听 8080；**不做映射**，映射靠 `docker run -p` |
| 48-49 | `ENV SPRING_PROFILES_ACTIVE=dev JAVA_OPTS="..."` | 环境变量进镜像（可被 run 时 `-e` 覆盖）；JAVA_OPTS 用 `MaxRAMPercentage` 让 JVM 自动按容器内存限额取堆 |
| 52 | `ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]` | 容器启动时执行的命令；用 sh -c 是为了能展开 `$JAVA_OPTS` 变量 |

> 整份 Dockerfile 的"层"结构 = 基础镜像 → 依赖层(19) → 源码层(22-23) → jar 拷贝层(37) → 配置层(48) → 启动命令(52)。层越靠后，改动成本越低。这就是"分层缓存"落地的具体样子。

---

### 16.2 docker-compose.yml 逐行精读（企业开发环境"一条命令起全套"样例）

> 你已会用 `docker run`，compose 只是把多个 run 写进一个 yml 统一管理。下面的文件 = `mysql + redis + 你的后端`，一个 `docker compose up -d` 全起。

```yaml
# 文件位置建议：项目根目录 docker-compose.yml（backend 上一级），
# 和后端镜像放一起便于 build 引用
services:                                # 定义一组服务（每个服务=一个容器）
  mysql:                                 # 服务名（也是容器名前缀 + 内网域名）
    image: mysql:8.0                     # 用的镜像（本地没有会自动拉）
    container_name: dev-mysql            # 容器名（可选，起名后 docker ps 好认）
    restart: always                      # 挂了/重启自动拉起（= run 的 --restart=always）
    ports:                               # 端口映射数组（= 多个 -p）
      - "3306:3306"                      # 宿主机3306:容器3306
    environment:                         # 环境变量数组（= 多个 -e）
      MYSQL_ROOT_PASSWORD: "123456"      # 首次初始化 root 密码
    volumes:                             # 挂载数组（= 多个 -v）
      - mysql-data:/var/lib/mysql        # 命名卷:容器数据目录（数据持久化，删容器不丢）
    networks:                            # 加入哪个自定义网络（可省略=默认网络）
      - app-net                          # 同一网络的容器能用【服务名】互相访问

  redis:                                 # 第二个服务
    image: redis:7
    container_name: dev-redis
    restart: always
    ports:
      - "6379:6379"
    networks:
      - app-net

  backend:                               # 第三个服务：你的 Spring Boot
    build:                               # 不用现成镜像，用 build 现场构建
      context: ./backend                 # 构建上下文 = backend 目录（找它的 Dockerfile）
      dockerfile: Dockerfile             # 指定 Dockerfile 名（默认就是这个名字）
    image: research-agent:1.0            # 构建产物镜像名（build 结果打这个 tag）
    container_name: research-agent-dev
    restart: always
    depends_on:                          # 启动顺序依赖（先起依赖的服务）
      - mysql                            # 等 mysql/redis 容器先启动
      - redis
    ports:
      - "8080:8080"
    environment:                         # 覆盖/新增容器内环境变量
      SPRING_PROFILES_ACTIVE: dev        # = docker run -e SPRING_PROFILES_ACTIVE=dev
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/testdb?useSSL=false
      # ↑ 数据库地址写 mysql = compose 内的服务域名，compose 会自动解析到 mysql 容器
    networks:
      - app-net

volumes:                                 # 顶层声明"命名卷"（容器删了卷还在）
  mysql-data:                            # 名字要和上面 services 里引用的一致
```

**compose 常用命令**（对应 docker 单容器命令）：

```bash
docker compose up -d              # 按 yml 启动全部（-d 后台）
docker compose up -d --build      # 先重新 build 再起（代码改了用它）
docker compose down               # 停+删全部容器（卷默认保留）
docker compose down -v            # 连命名卷一起删（⚠️ 数据没了，慎用）
docker compose ps                 # 看本 yml 的服务状态
docker compose logs -f backend    # 只看 backend 服务日志
docker compose exec backend bash  # 进 backend 容器（= docker exec）
```

> compose 与 K8s 的对应关系（帮你建立全局观）：compose 的 `services/volumes/networks` ≈ K8s 的 `Deployment/PersistentVolume/NetworkPolicy`，都是"声明式描述"，只是编排层级不同（单机 vs 集群）。学会 compose 再看 K8s YAML 会亲切很多。

---

### 16.3 Kubernetes Deployment + Service 逐行精读（research-agent.yaml）

#### Deployment 部分逐行

```yaml
apiVersion: apps/v1                    # API 分组与版本：apps 组 v1 版（Deployment 的家）
kind: Deployment                       # 资源类型：Deployment（管无状态应用）
metadata:                              # 元数据段（描述"这个资源是谁"）
  name: research-agent                 # 资源名字（kubectl get deployment research-agent）
spec:                                  # 期望状态（描述"想要什么"，K8s 负责达成）
  replicas: 2                          # 期望永远保持 2 个 Pod（声明式核心）
  selector:                            # 选择器（Deployment 靠它"认领"自己的 Pod）
    matchLabels:                       # 匹配规则：Pod 必须带以下标签才归它管
      app: research-agent              # 标签键值（门牌号）
  template:                            # Pod 模板（要创建的 Pod 长什么样）
    metadata:                          # Pod 的元数据
      labels:                          # Pod 的标签（挂门牌，selector 认的就是它）
        app: research-agent            # 必须与 selector.matchLabels 完全一致，否则 apply 报错
    spec:                              # Pod 自己的期望状态（注意缩进位置变了）
      containers:                      # Pod 内容器列表
        - name: research-agent         # 容器名（同 Pod 内唯一；日志 kubectl logs 用它）
          image: research-agent:1.1    # 用哪个镜像（滚动更新改的就是这行）
          ports:                       # 端口声明数组
            - containerPort: 8080      # 容器内应用监听的端口（纯声明，不映射）
```

#### Service 部分逐行

```yaml
---
apiVersion: v1                         # Service 属于核心 v1 组（不用带分组名）
kind: Service                          # 资源类型：Service（给 Pod 稳定的访问入口）
metadata:
  name: research-agent-svc             # Service 名字（集群内域名=这个名字）
spec:
  type: NodePort                       # 暴露方式：NodePort=每个节点开个对外端口
  selector:                            # 转发目标：挑带以下标签的 Pod
    app: research-agent                # 与 Deployment 的 Pod 标签一致 → 流量转发给它
  ports:                               # 端口定义
    - port: 8080                       # Service 自己的端口（集群内部访问 10.43.x.x:8080）
      targetPort: 8080                 # 转发到 Pod 容器的哪个端口（对应 containerPort）
      nodePort: 30081                  # 节点对外端口（外部访问 节点IP:30081）
```

#### 部署/验证命令逐条拆

```bash
kubectl apply -f research-agent.yaml   # 提交期望状态（幂等，跑两次结果一样）
kubectl get pods                       # 查询：是否达到 2 个 Running
kubectl get svc research-agent-svc     # 查询：Service 是否生成
kubectl get endpoints research-agent-svc  # 关键！Service 实际转发给哪些 Pod IP
kubectl describe pod <名字>            # 出问题时的病历本（Events 写原因）
kubectl logs deployment/research-agent # 看日志（多 Pod 随机挑 1 个；想全看加 -l）
```

#### 一份可对照的"状态 vs 期望"观察

```bash
# 声明是 2 副本，看看 K8s 眼里实际几个
kubectl get deployment research-agent -o jsonpath='{.status.replicas}'
# 输出会和 replicas:2 对上；如果小于 2，说明有 Pod 没起来，用 describe 查

# selector 漂移排查（Service 找不到 Pod 时）
kubectl get svc research-agent-svc -o jsonpath='{.spec.selector}'
kubectl get pods --show-labels   # 两边 label 对不上 = 白转发
```

> 记忆锚点：**Deployment 管"生"（Pod 数量与版本），Service 管"通"（把请求转到活着的 Pod）**。二者通过 `app: research-agent` 这个 label 握手，互不直接引用对方的名称——这就是 K8s 的解耦设计。
