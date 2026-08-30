# 启动 Redis（如果未运行）
$redisService = Get-Service Redis -ErrorAction SilentlyContinue
if ($redisService -and $redisService.Status -ne "Running") {
    Write-Host "Starting Redis..." -ForegroundColor Yellow
    Start-Service Redis
} elseif ($redisService -and $redisService.Status -eq "Running") {
    Write-Host "Redis is already running." -ForegroundColor Cyan
} else {
    Write-Host "Redis service not found. Skipping." -ForegroundColor Red
}

# 启动 dev 环境（MyBatis 持久化模式）
$env:SPRING_PROFILES_ACTIVE="dev"
$env:APP_PERSISTENCE_MODE="mybatis"
$env:APP_EXECUTION_MODE="ASYNC"
$env:SERVER_PORT="8080"

Write-Host "Starting research-agent-backend (dev profile, MyBatis mode)..." -ForegroundColor Green
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
