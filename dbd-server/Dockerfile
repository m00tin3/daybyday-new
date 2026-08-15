# ============================================================
# Day-BY-Day 后端镜像（多阶段构建）
#   阶段 1：Maven 构建（JDK 17）
#   阶段 2：JRE 17 运行
# 构建：docker build -t dbd-server .
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先复制 pom 下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline || true
# 再复制源码构建
COPY src ./src
RUN mvn -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/dbd-server.jar app.jar
ENV TZ=Asia/Shanghai \
    MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    MYSQL_USER=root \
    MYSQL_PASSWORD=1234 \
    REDIS_HOST=redis \
    REDIS_PORT=6379
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
