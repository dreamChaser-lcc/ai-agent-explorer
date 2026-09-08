# ============================================================
# research-agent-backend 多阶段构建 Dockerfile
# 第1阶段：Maven 完整镜像负责编译（编译工具不进入最终镜像）
# 第2阶段：精简 JRE 镜像只负责运行（镜像体积小、启动快）
# ============================================================

# ---------- 第1阶段：编译 ----------
# 通过 DaoCloud 加速源拉取官方镜像（国内网络直连 docker.io 不通时的标准做法）
# 内置 Maven 3.9 + JDK 21
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /build

# 先复制 pom.xml 和 Maven 镜像源配置，单独下载依赖（利用 Docker 分层缓存：
# 代码改动时只要 pom 没变，依赖层直接复用缓存，不用重新下载）
COPY pom.xml ./
COPY docker/maven-settings.xml /root/.m2/settings.xml
RUN mvn dependency:go-offline -B

# 再复制源码并打包（-DskipTests 跳过测试，镜像构建不跑单测）
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- 第2阶段：运行 ----------
# 同样走加速源；基于 eclipse-temurin 官方 JRE 精简镜像（只有运行环境，没有编译工具）
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

# 设置时区为中国时区（日志时间与本地一致，方便排查）
ENV TZ=Asia/Shanghai

# 创建非 root 用户运行应用（企业安全规范：容器内不使用 root）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 设置工作目录并复制编译产物（只复制 jar，不带编译工具链）
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

# 以非 root 用户运行
USER appuser

# 声明容器对外提供服务的端口（文档作用，实际映射由 docker run -p 决定）
EXPOSE 8080

# 健康检查依赖的启动参数通过环境变量注入，支持外部覆盖
# 默认使用 dev 配置（H2 内存库），容器内可独立运行
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# 启动命令：JAVA_OPTS 用于注入 JVM 参数（容器内存限制场景必备）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
