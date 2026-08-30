# 启动 Redis（如果未运行）
$redisService = Get-Service -Name "Redis" -ErrorAction SilentlyContinue
if (-not $redisService) {
    # 尝试通过 sc.exe 查询
    $svcInfo = sc.exe query Redis 2>&1
    if ($svcInfo -match "RUNNING") {
        Write-Host "Redis is already running." -ForegroundColor Cyan
    } elseif ($svcInfo -match "STOPPED") {
        Write-Host "Starting Redis..." -ForegroundColor Yellow
        sc.exe start Redis
    } else {
        Write-Host "Redis service not found. Skipping." -ForegroundColor Red
    }
} elseif ($redisService.Status -ne "Running") {
    Write-Host "Starting Redis..." -ForegroundColor Yellow
    Start-Service Redis
} else {
    Write-Host "Redis is already running." -ForegroundColor Cyan
}

# 启动 dev 环境
$env:SPRING_PROFILES_ACTIVE="dev"
$env:APP_PERSISTENCE_MODE="jpa"
$env:APP_EXECUTION_MODE="ASYNC"
$env:SERVER_PORT="8080"

Write-Host "Starting research-agent-backend (dev profile)..." -ForegroundColor Green
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
