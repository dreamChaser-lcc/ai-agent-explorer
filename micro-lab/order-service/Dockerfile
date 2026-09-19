# ============================================================
# order-service 镜像构建文件（单阶段简化版）
# 构建前提：已在 micro-lab 根目录执行 mvn clean package -DskipTests（产出 jar）
# 构建命令（在 micro-lab 根目录）：
#   docker build -t order-service:1.0.0 order-service
# ============================================================

# 基础镜像：JRE 21 精简版。直写 daocloud 加速源域名，绕开拉取被墙的问题
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

# 时区：让容器内日志时间与本地一致
ENV TZ=Asia/Shanghai

WORKDIR /app

# 拷贝本地已构建的 jar（本地构建 + 镜像只装运，比容器内编译快得多）
COPY target/order-service-1.0.0-SNAPSHOT.jar app.jar

# 启动命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
