# Sentinel Dashboard 自构建镜像
# 背景：bladex/sentinel-dashboard 不在 daocloud 加速白名单（403 拒绝），
#       改用「官方 jar + 可拉取的基础镜像」自建。
# 制品来源：maven 中央仓库 com.alibaba.csp:sentinel-dashboard:1.8.8（阿里云 maven 镜像下载）
# 构建方式（在本目录执行，jar 与本文件同级）：
#   docker build -t sentinel-dashboard:1.8.8 .

FROM docker.m.daocloud.io/library/eclipse-temurin:8-jre-alpine

ENV TZ=Asia/Shanghai
WORKDIR /app

COPY sentinel-dashboard-1.8.8.jar app.jar

EXPOSE 8858

ENTRYPOINT ["java", "-Dserver.port=8858", "-Dcsp.sentinel.dashboard.server=localhost:8858", "-Dproject.name=sentinel-dashboard", "-jar", "/app/app.jar"]
