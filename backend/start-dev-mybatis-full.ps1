# ============================================================
# 本地开发一键启动（dev profile + MyBatis 持久化模式）—— 完整版
# 来源：复制 start-dev.ps1，唯一实质差异 = APP_PERSISTENCE_MODE 改为 mybatis
# 顺序：清理占用端口/数据库的旧进程 → 启动 Redis → 设环境变量 → 启动应用
# ============================================================

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

# ============================================================
# 0. 启动前清理（同 start-dev.ps1）
#    解决两类历史踩坑：
#    ① 端口 8080 被上一个实例占用 → "Port 8080 was already in use"
#    ② H2 文件被另一个 JVM 独占   → "The file is locked" →
#       进而报 "Unable to determine Dialect"（这个报错是症状，不是病因）
#    注意：H2 是【文件模式】数据库，同一时刻默认只允许一个 JVM 打开。
# ============================================================
$port = 8080
Write-Host "[0/3] 检查端口 $port ..." -ForegroundColor Cyan

$lines = netstat -ano | Select-String ":$port\s" | Select-String "LISTENING"
if ($lines) {
    $pids = $lines | ForEach-Object { ($_.Line -split '\s+')[-1] } | Sort-Object -Unique
    foreach ($processId in $pids) {
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "      端口 $port 被 PID=$processId ($($proc.ProcessName)) 占用，正在停止..." -ForegroundColor Yellow
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 3
    $still = netstat -ano | Select-String ":$port\s" | Select-String "LISTENING"
    if ($still) {
        Write-Host "      ✗ 端口 $port 仍被占用，请手动检查" -ForegroundColor Red
        exit 1
    }
    Write-Host "      端口 $port 已释放" -ForegroundColor Green
} else {
    Write-Host "      端口 $port 空闲" -ForegroundColor Green
}

# H2 文件锁兜底检查（端口空闲但库文件被别的 java 进程锁住的情况）
$dbFile = Join-Path $PSScriptRoot "data\h2db\research-agent.mv.db"
if (Test-Path $dbFile) {
    try {
        $stream = [System.IO.File]::Open($dbFile, 'Open', 'ReadWrite', 'None')
        $stream.Close()
        Write-Host "      H2 数据库文件未被锁定" -ForegroundColor Green
    } catch {
        Write-Host "      ✗ H2 数据库文件被其他进程锁住：$dbFile" -ForegroundColor Red
        Write-Host "        请关闭其余实例（IDE / 另一个终端），或执行：" -ForegroundColor Yellow
        Write-Host "        Get-Process java | Stop-Process -Force" -ForegroundColor Yellow
        exit 1
    }
}
# ============================================================
# 1. 启动 Redis（如果未运行）
# ============================================================
Write-Host "[1/3] 检查 Redis ..." -ForegroundColor Cyan
$redisService = Get-Service -Name "Redis" -ErrorAction SilentlyContinue
if (-not $redisService) {
    $svcInfo = sc.exe query Redis 2>&1
    if ($svcInfo -match "RUNNING") {
        Write-Host "      Redis 已运行" -ForegroundColor Green
    } elseif ($svcInfo -match "STOPPED") {
        Write-Host "      正在启动 Redis..." -ForegroundColor Yellow
        sc.exe start Redis
    } else {
        Write-Host "      未找到 Redis 服务，跳过（6379 通即可）" -ForegroundColor Red
    }
} elseif ($redisService.Status -ne "Running") {
    Write-Host "      正在启动 Redis..." -ForegroundColor Yellow
    Start-Service Redis
} else {
    Write-Host "      Redis 已运行" -ForegroundColor Green
}

# ============================================================
# 2. 环境变量（★ 与本脚本的唯一实质差异：mybatis 而非 jpa）
# ============================================================
Write-Host "[2/3] 设置环境变量 ..." -ForegroundColor Cyan
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:APP_PERSISTENCE_MODE = "mybatis"      # ★ MyBatis 持久化模式（练习用这个）
$env:APP_EXECUTION_MODE = "ASYNC"
$env:SERVER_PORT = "8080"

# ===== LLM 配置（场景⑧：本地一键启动用）=====
# ⚠️ 原 start-dev.ps1 里 API Key 为明文硬编码——本脚本【已注释】，避免再复制一份密钥进仓库。
#    需要 LLM 功能时，把下面一行取消注释并填入自己的 key（或用系统环境变量注入）。
# $env:OPENAI_API_KEY = "sk-填你自己的"

# ★ 本地启动必须禁用 Nacos 配置中心：远程配置（集群版 PG+Flyway）优先级高于本地 dev yml，
#   会把 H2 覆盖成 PG 导致启动失败（2026-10-21 实测）
$env:JAVA_TOOL_OPTIONS = "-Dspring.cloud.nacos.config.enabled=false"

# 手写客户端（APP_LLM_CLIENT_IMPL=http）的代码层代理（需要 LLM 时再配）
# $env:APP_LLM_PROXY_HOST = "127.0.0.1"
# $env:APP_LLM_PROXY_PORT = "7897"

# ============================================================
# 3. 启动应用（dev profile + MyBatis 模式 + H2 文件库）
# ============================================================
Write-Host "[3/3] 启动 research-agent-backend (dev profile, MyBatis mode) ..." -ForegroundColor Green
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"