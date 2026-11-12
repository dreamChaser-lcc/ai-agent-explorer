# ============================================================
# 统一启动入口（按学习内容叠加 profile）
#
#   .\start.ps1                     -> lite：零依赖（H2 + LLM mock，8~20 秒起）
#   .\start.ps1 -Mode mybatis       -> lite + MyBatis（SP11 练习）
#   .\start.ps1 -Mode redis         -> lite + Redis 缓存（场景⑦，需 Redis 6379）
#   .\start.ps1 -Mode redis,kafka   -> lite + Redis + Kafka（多模块可叠加）
#   .\start.ps1 -Mode llm           -> lite + 真实 LLM（需外网 + 代理）
#   .\start.ps1 -Mode dev           -> 旧 dev profile（H2 + 原有依赖行为，向后兼容）
#   .\start.ps1 -Mode cluster       -> 默认 profile（PG + Redis + Kafka + Nacos，需全部就绪）
# ============================================================
param(
    [string[]]$Mode = @("lite")
)

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

# ---------- 1. 端口清理（8080 被占则杀进程） ----------
$port = 8080
if ($env:SERVER_PORT) { $port = [int]$env:SERVER_PORT }

Write-Host "[1/3] 检查端口 $port ..." -ForegroundColor Cyan
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
    Start-Sleep -Seconds 2
    Write-Host "      端口已释放" -ForegroundColor Green
} else {
    Write-Host "      端口空闲" -ForegroundColor Green
}

# ---------- 2. 组装 profile ----------
$modes = @($Mode | ForEach-Object { $_ -split ',' }) | Where-Object { $_ }

if ($modes -contains "cluster") {
    $profiles = @()
    Write-Host "[2/3] 模式 = cluster（默认 profile：PG + Redis + Kafka + Nacos）" -ForegroundColor Yellow
} elseif ($modes -contains "dev") {
    $profiles = @("dev")
    Write-Host "[2/3] 模式 = dev（旧 dev profile，向后兼容）" -ForegroundColor Yellow
} else {
    $profiles = @("lite") + ($modes | Where-Object { $_ -ne "lite" })
    Write-Host ("[2/3] 模式 = " + ($profiles -join ",") + "（基础 lite + 模块叠加）") -ForegroundColor Yellow
}

# ---------- 3. 环境变量 ----------
$profileLine = $profiles -join ","
if ($profiles.Count -eq 0) {
    $env:SPRING_PROFILES_ACTIVE = ""
} else {
    $env:SPRING_PROFILES_ACTIVE = $profileLine
}

$persistenceMode = "mybatis"
if ($modes -contains "jpa") { $persistenceMode = "jpa" }
$env:APP_PERSISTENCE_MODE = $persistenceMode
$env:APP_EXECUTION_MODE = "ASYNC"
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = "8080" }

# 通用：本地一律禁用 Nacos（cluster 模式除外——cluster 走默认 profile 自己配）
if ($modes -notcontains "cluster") {
    $env:JAVA_TOOL_OPTIONS = "-Dspring.cloud.nacos.config.enabled=false -Dspring.cloud.nacos.discovery.enabled=false"
}

# llm 模式需要 key/代理（明文 key 由项目负责人兜底；环境变量优先）
if ($modes -contains "llm") {
    if (-not $env:OPENAI_API_KEY) {
        $env:OPENAI_API_KEY = "sk-gONcRZzpgzB8N0Rsw84pYVe28mAgnj7hJ5KHDRxvQ0EGUIQW"
    }
    $env:APP_LLM_PROXY_HOST = "127.0.0.1"
    $env:APP_LLM_PROXY_PORT = "7897"
}

# ---------- 4. 启动 ----------
if ($profiles.Count -eq 0) {
    Write-Host "[3/3] 启动 research-agent-backend（profiles=default）" -ForegroundColor Green
    mvn spring-boot:run
} else {
    Write-Host ("[3/3] 启动 research-agent-backend（profiles=" + $profileLine + "）") -ForegroundColor Green
    mvn spring-boot:run "-Dspring-boot.run.profiles=$profileLine"
}
