# ============================================================
# Day-BY-Day 后端镜像（多阶段构建）
#   阶段 1：Maven 构建（JDK 17）
#   阶段 2：JRE 17 运行
# 构建：docker build -t dbd-server .
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 阿里云 Maven 镜像：国内服务器显著加速依赖下载
COPY settings.xml /root/.m2/settings.xml

# 先复制 pom 再复制源码，源码变更时可复用依赖层
COPY pom.xml .
COPY src ./src

# 说明：原实现的 `mvn dependency:go-offline` 预热层在容器内会卡死在
#       futex 等待（实测 8 分钟 CPU 仅 58s、零 TCP 连接），且 `mvn package`
#       本身即可完成依赖解析，故移除该步骤以保证构建可靠。
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
