# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存层加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="queue-system"

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制 JAR
COPY --from=builder /build/target/queue-system-backend-1.0.0.jar app.jar

# 创建外部配置目录
RUN mkdir -p /app/config && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# spring.config.additional-location 让外部挂载的 application.yml 优先
# spring.profiles.active=prod 激活生产配置
ENTRYPOINT ["java", \
    "--add-opens=java.base/java.lang=ALL-UNNAMED", \
    "--add-opens=java.base/java.nio=ALL-UNNAMED", \
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
    "--add-opens=java.base/java.util=ALL-UNNAMED", \
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", \
    "--add-opens=java.base/java.net=ALL-UNNAMED", \
    "-jar", "app.jar", \
    "--spring.config.additional-location=file:/app/config/", \
    "--spring.profiles.active=prod"]
