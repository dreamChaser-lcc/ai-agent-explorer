# ============================================================
# 本地开发一键启动（dev + dev-docker 组合 profile）
# 与 start-dev.ps1 的区别：中间件全部来自 Docker 栈（compose），
#   数据源用 PostgreSQL（容器内 Flyway 已建表），Nacos 连本地 Docker Nacos。
# 前置：backend/docker/docker-compose.yml 的栈（脚本会自动拉起并停掉容器化 backend）
#（OpenSpec 变更 add-docker-runtime-environment，2026-11-16）
# ============================================================

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

# ============================================================
# 0. Docker daemon 守卫（必须最先：没有 Docker 后面全部白搭）
# ============================================================
Write-Host "[0/3] 检查 Docker daemon ..." -ForegroundColor Cyan

if (-not (docker version --format "{{.Server.Version}}" 2>$null)) {
    Write-Host "✗ Docker Desktop 未运行——本模式依赖 Docker 中间件栈" -ForegroundColor Red
    Write-Host "  请先启动 Docker Desktop（等待鲸鱼图标稳定后再重跑本脚本）" -ForegroundColor Yellow
    exit 1
}
Write-Host "      Docker daemon 就绪" -ForegroundColor Green

# ============================================================
# 1. 释放 8080 并确保中间件栈就绪
#
#    ★ 两个关键点（早期版本的 bug 教训）：
#    ① 先用 compose 优雅停掉容器化 backend——它是 8080 的合法占用者；
#    ② 进程级清理【只杀 java/javaw】——绝不 kill com.docker.backend / wslrelay，
#       杀它们会直接搞崩 Docker Desktop（旧版脚本"跑一次 Docker 就没了"的根因）。
# ============================================================
Write-Host "[1/3] 释放 8080 并确保中间件栈就绪 ..." -ForegroundColor Cyan

docker compose -f "docker\docker-compose.yml" stop backend 2>&1 | Out-Null

$port = 8080
$lines = netstat -ano | Select-String ":$port\s" | Select-String "LISTENING"
if ($lines) {
    $pids = $lines | ForEach-Object { ($_.Line -split '\s+')[-1] } | Sort-Object -Unique
    foreach ($processId in $pids) {
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        if ($proc.ProcessName -match '^(java|javaw)$') {
            Write-Host "      端口 $port 被 PID=$processId ($($proc.ProcessName)) 占用，正在停止..." -ForegroundColor Yellow
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        } else {
            Write-Host "      端口 $port 被 PID=$processId ($($proc.ProcessName)) 占用——非 java 进程，脚本不停止" -ForegroundColor Yellow
            Write-Host "        （Docker/WSL 相关进程受保护；若持续占用请手动排查）" -ForegroundColor Yellow
        }
    }
    Start-Sleep -Seconds 2
}

# 中间件四件套拉起
# 注意：docker compose 的状态信息走 stderr（Docker CLI 惯例），
# PowerShell 会把原生命令的 stderr 包装成 NativeCommandError 显示成红字，
# 因此这里先捕获输出、只在失败时完整打印，成功时只给一行摘要。
$composeOutput = docker compose -f "docker\docker-compose.yml" up -d nacos postgres redis kafka 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host $composeOutput -ForegroundColor Red
    Write-Host "✗ 中间件栈启动失败（见上方 docker 输出）" -ForegroundColor Red
    exit 1
}
Write-Host "      中间件栈已就绪（nacos / postgres / redis / kafka）" -ForegroundColor Green

# 等待关键端口就绪（最多 30 秒；Docker 端口转发就绪有延迟属正常）
$targetPorts = 5432, 6379, 9092, 8848
$deadline = (Get-Date).AddSeconds(30)
foreach ($p in $targetPorts) {
    $ready = $false
    while (((Get-Date) -lt $deadline) -and -not $ready) {
        if (netstat -ano | Select-String ":$p\s" | Select-String "LISTENING") {
            $ready = $true
            Write-Host "      端口 $p 就绪" -ForegroundColor Green
        } else {
            Start-Sleep -Seconds 2
        }
    }
}

# ============================================================
# 2. 环境变量（组合 profile：dev,dev-docker）
# ============================================================
Write-Host "[2/3] 设置环境变量 ..." -ForegroundColor Cyan
# 单 profile 全量独立配置（不叠加 dev）——见 application-dev-docker.yml
$env:SPRING_PROFILES_ACTIVE = "dev-docker"
# ★ config.import 解析早于 profile 生效，Nacos 地址必须用 env 注入（实测踩坑）
$env:NACOS_ADDR = "localhost:8848"
# 其余配置（持久化 jpa / 执行模式 ASYNC / 端口 8080）直接走 application.yml 的
# 占位符默认值，无需在此重复声明——只有需要偏离默认值时才加 env

# ===== LLM 配置：API Key 从 docker/.env 读取（不硬编码进脚本，避免明文入库）=====
$dotenvPath = Join-Path $PSScriptRoot "docker\.env"
if (Test-Path $dotenvPath) {
    foreach ($line in Get-Content $dotenvPath -Encoding UTF8) {
        if ($line -match '^\s*OPENAI_API_KEY\s*=\s*(.+)\s*$') {
            $env:OPENAI_API_KEY = $Matches[1].Trim()
            break
        }
    }
}
if (-not $env:OPENAI_API_KEY) {
    Write-Host "      ⚠️ 未找到 OPENAI_API_KEY（backend/docker/.env），LLM 功能将走失败降级" -ForegroundColor Yellow
}

# ===== LLM 出网代理（同 start-dev.ps1：nonProxyHosts 已排除 localhost/内网）=====
# ★ preferIPv4Stack=true：本机 wslrelay.exe 会在所有端口的 [::1]（IPv6 回环）上监听
#   （WSL 的 localhost 转发机制），Java 连 localhost 优先解析 IPv6 会撞上它 → refused。
#   强制 IPv4 后 localhost 解析为 127.0.0.1，直接命中 Docker 的端口映射（实测踩坑）。
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttp.nonProxyHosts=localhost|127.*|192.168.*"
$env:APP_LLM_PROXY_HOST = "127.0.0.1"
$env:APP_LLM_PROXY_PORT = "7897"

# 注意：与 start-dev.ps1 不同——这里【不】禁用 Nacos 配置中心。
# dev-docker 连的是本地 Docker Nacos（空库），optional 前缀兜底安全；
# start-dev.ps1 禁用的原因是它连虚拟机 Nacos，会拉到集群 PG 配置覆盖 H2（实测踩坑）。

# ============================================================
# 3. 启动应用（dev,dev-docker 组合 profile）
# ============================================================
Write-Host "[3/3] 启动 research-agent-backend (dev,dev-docker profiles) ..." -ForegroundColor Green
mvn spring-boot:run
