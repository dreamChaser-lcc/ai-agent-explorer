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

# ★ 禁用 Nacos（config + discovery）：config 防远程配置把 H2 覆盖成 PG；discovery 防注册失败中断启动
# ★ 跳过 Kafka topic 自动创建（本地没起 Kafka 时少等 40 秒超时；不影响练习）
$env:JAVA_TOOL_OPTIONS = "-Dspring.cloud.nacos.config.enabled=false -Dspring.cloud.nacos.discovery.enabled=false -Dspring.kafka.admin.auto-create=false"

# 启动 dev 环境（MyBatis 持久化模式）
$env:SPRING_PROFILES_ACTIVE="dev"
$env:APP_PERSISTENCE_MODE="mybatis"
$env:APP_EXECUTION_MODE="ASYNC"
$env:SERVER_PORT="8080"

# LLM 配置（场景⑧：本地一键启动；明文 key 由项目负责人兜底）
$env:OPENAI_API_KEY = "sk-gONcRZzpgzB8N0Rsw84pYVe28mAgnj7hJ5KHDRxvQ0EGUIQW"
$env:APP_LLM_PROXY_HOST = "127.0.0.1"
$env:APP_LLM_PROXY_PORT = "7897"

Write-Host "Starting research-agent-backend (dev profile, MyBatis mode)..." -ForegroundColor Green
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"