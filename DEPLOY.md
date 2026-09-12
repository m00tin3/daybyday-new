# Day-BY-Day 部署指南

项目已按**可上线模式**开发（配置、Docker 镜像、部署脚本均按生产标准）。支持两条路径：

1. **Docker Compose 一键部署**（任何有 Docker 的服务器）
2. **免备案海外免费平台**（Railway / Render 等，无需 ICP 备案，适合快速上线演示）

> 国内云服务器（阿里云/腾讯云/华为云）部署前请先阅读 [§2 国内服务器必做](#二国内服务器必做重要)。

## 一、Docker Compose 部署（通用）

```bash
# 1. 拉取代码
git clone <仓库地址> dbd && cd dbd

# 2. 配置环境变量（必做！含全部密码）
cp .env.example .env
vi .env          # 至少修改 MYSQL_PASSWORD 与 REDIS_PASSWORD

# 3. 一键启动（首次构建约 3-5 分钟）
docker compose up -d --build

# 4. 查看状态与日志
docker compose ps
docker compose logs -f backend
```

> ⚠️ **`.env` 是必填项**：`docker-compose.yml` 对 `MYSQL_PASSWORD` / `REDIS_PASSWORD`
> 使用了 `${VAR:?}` 校验，未设置时 compose 会**直接拒绝启动**。
> 这是刻意设计——避免用默认弱密码把数据库和缓存暴露出去。

访问：

| 入口 | 地址 |
|---|---|
| 前端 | `http://服务器IP`（WEB_PORT 默认 80） |
| 前端备用入口 | `http://服务器IP:8090`（WEB_PORT_ALT） |
| 接口文档 | `http://服务器IP/swagger-ui.html` |
| 健康检查 | `http://服务器IP/api/health` |

### 环境变量（`.env`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_PASSWORD` | **必填** | MySQL root 密码，同时作为业务库密码 |
| `REDIS_PASSWORD` | **必填** | Redis 访问密码，禁止留空 |
| `WEB_PORT` | 80 | 前端主入口端口 |
| `WEB_PORT_ALT` | 8090 | 前端备用入口端口 |
| `BACKEND_PORT` | 8080 | 后端端口（**仅绑定 127.0.0.1**，Swagger 调试走 SSH 隧道） |
| `MYSQL_PORT` | 3306 | MySQL 端口（**仅绑定 127.0.0.1**） |
| `REDIS_PORT` | 6379 | Redis 端口（**仅绑定 127.0.0.1**） |
| `SQL_LOG_IMPL` | `NoLoggingImpl` | 生产默认关闭 SQL 打印 |
| `LOG_LEVEL` | info | 后端日志级别 |
| `SMS_MOCK` | true | 演示验证码开关 |

### 安全设计（公网服务器务必理解）

编排已做如下约束，**不要为了图方便把它们改回公网**：

- **MySQL / Redis / 后端一律只绑定 `127.0.0.1`**，外网无法直连；只有前端 nginx 对外提供服务
- Redis 强制 `requirepass`，且开启 **AOF 持久化**
  （秒杀库存、一人一单标记、签到 BitMap、Feed 时间线无法从 DB 完整重建，
  不持久化会导致 Redis 重启后库存重置、可重复抢购）
- 全部容器 `restart: unless-stopped`，服务器重启后自动拉起
- 需要连数据库排查时用 SSH 隧道，不要开放端口：
  ```bash
  ssh -L 3306:127.0.0.1:3306 root@服务器IP    # 本地连 127.0.0.1:3306
  ssh -L 8080:127.0.0.1:8080 root@服务器IP    # 本地看 Swagger
  ```

### 数据持久化

- MySQL 数据卷：`mysql-data`（重建容器不丢数据）
- Redis 数据卷：`redis-data`（AOF 文件，重启不丢计数/库存/时间线）
- 演示数据在 MySQL 首次初始化时自动灌入（`init.sql`）

### 常用运维

```bash
docker compose logs -f backend      # 后端日志
docker compose restart backend      # 重启后端
docker compose down                 # 停止（保留数据卷）
docker compose down -v              # 停止并删除数据卷（重置全部数据）
docker compose up -d --build        # 改代码后重新部署
```

## 二、国内服务器必做（重要）

国内机房有若干**会直接导致部署失败**的坑，以下是实测结论。

### 2.1 Docker 镜像站

**国内多数公共 Docker 镜像站已于 2024 年后关停**，服务器上若配着旧镜像站，
`docker compose up` 会在拉取 `mysql:8.0` 时直接失败。

检查当前配置与实际可用性：

```bash
docker info | grep -A5 "Registry Mirrors"

# 探测候选镜像站（返回 200/401 即为存活）
for m in docker.1panel.live docker.m.daocloud.io docker.1ms.run docker.xuanyuan.me; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 8 "https://$m/v2/")
  printf "  %-28s -> %s\n" "$m" "${code:-不可达}"
done
```

配置可用镜像站（`/etc/docker/daemon.json`），然后 `systemctl restart docker`：

```json
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ]
}
```

> 阿里云用户也可在控制台「容器镜像服务 → 镜像加速器」获取专属地址，稳定性更好。
> 注意：腾讯云内网地址 `mirror.ccs.tencentyun.com` 在阿里云上**无法解析**，不要混用。

### 2.2 Maven 与 npm 依赖源

- **Maven**：仓库已内置 `dbd-server/settings.xml`（阿里云公共仓库），
  构建时自动复制进镜像，无需额外配置。
- **npm**：`dbd-web/Dockerfile` 已内置 `registry.npmmirror.com`。
  实测国内服务器访问 `registry.npmjs.org` **完全不可达**（25s 超时），
  不配置镜像前端镜像必然构建失败。

### 2.3 小内存服务器（≤2GB）

`MySQL 8 + Spring Boot + Node 构建` 在 1.7GB 内存的机器上很容易触发 OOM。建议：

```bash
# 1) 建 2G swap（阿里云 Ubuntu 镜像默认 0 swap，构建期必用）
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

编排中已包含针对小内存的调优，无需改动：

- MySQL：`--performance-schema=OFF`、`--innodb-buffer-pool-size=128M`（省 200-400MB）
- 后端 JVM：`JAVA_TOOL_OPTIONS: -Xmx384m -XX:MaxMetaspaceSize=160m`

### 2.4 备案与 80 端口

- 国内提供公开 UGC 社区服务需 **ICP 备案**（个人难办理）
- 云厂商会对**未备案域名**解析到境内 IP 的 80/443 请求做拦截；
  **直接用公网 IP 访问 80 端口实测可用**（本项目已在阿里云验证）
- 若不放心，前端已同时监听 **80 与 8090**，两个都放行即可留后路

安全组/防火墙需放行：**80、8090**（MySQL/Redis/后端端口**不要放行**）。

## 三、非 Docker 手工部署（nginx + jar）

```bash
# 1. 数据库（注意指定 utf8mb4，否则中文会二次编码乱码）
mysql -u root -p --default-character-set=utf8mb4 < dbd-server/src/main/resources/db/init.sql

# 2. 后端
cd dbd-server && mvn -DskipTests package
nohup java -jar target/dbd-server.jar > run.log 2>&1 &
# 环境变量：MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MYSQL_PASSWORD/REDIS_HOST/REDIS_PORT/REDIS_PASSWORD

# 3. 前端
cd dbd-web && npm install && npm run build
# 将 dist/ 拷贝到 nginx html/，并应用 dbd-web/nginx.conf（proxy_pass 改为 127.0.0.1:8080）
```

## 四、常见问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `docker compose up` 报 `set MYSQL_PASSWORD in .env` | 未创建 `.env` | `cp .env.example .env` 并填密码 |
| 拉取镜像超时/失败 | 镜像站失效 | 见 [§2.1](#21-docker-镜像站) |
| 前端镜像构建卡在 `npm ci` | npm 源不可达 | 确认 Dockerfile 中 npmmirror 配置未被改动 |
| 页面中文全是 `å¹²é¥­é­‚` 乱码 | 导入时客户端字符集为 latin1，数据二次编码 | `init.sql` 开头已加 `SET NAMES utf8mb4;`；若历史数据已坏，删库重导：`DROP DATABASE dbd;` 后用 `--default-character-set=utf8mb4` 重新导入，并 `FLUSHALL` Redis 缓存 |
| `/v3/api-docs` 只返回几十字节 | springdoc 与 Spring Boot 版本不兼容 | 必须使用 `springdoc-openapi 2.9.1`（3.x 系列仅支持 Spring Boot 4）；2.6.0 会因 `ControllerAdviceBean` 构造函数被移除而抛 `NoSuchMethodError` |
| 构建卡在 `mvn dependency:go-offline` 不动 | 容器内 futex 死锁 | 已从 `dbd-server/Dockerfile` 移除该预热步骤 |
| 后端反复重启 / 被 OOM Kill | 内存不足 | 见 [§2.3](#23-小内存服务器2gb) |

## 五、合规说明（已知约束）

- 国内提供公开 UGC 评论/社区服务需 **ICP 备案**（经营性还需 ICP 许可证），个人办理周期长
- 本项目的部署路径：
  - **免备案演示**：海外免费层（Railway/Render/Oracle Cloud）
  - **国内正式上线**：备案后使用腾讯云/阿里云轻量服务器 + Docker Compose（部署方式不变）
- 演示模式验证码（`app.sms.mock=true`）：生产环境需改为 `false` 并接入短信服务商
  （`AuthServiceImpl` 中已预留切换点，改 `.env` 的 `SMS_MOCK` 即可）

## 六、上线前检查清单

- [x] `MYSQL_PASSWORD` / `REDIS_PASSWORD` 改为强密码（`.env`，强制必填）
- [x] 关闭 SQL 打印（`SQL_LOG_IMPL=NoLoggingImpl` 默认值）
- [x] 后端日志级别调为 info（`LOG_LEVEL=info` 默认值）
- [x] MySQL / Redis / 后端端口不对公网暴露（仅绑定 127.0.0.1）
- [x] Redis 开启密码与 AOF 持久化
- [x] 容器自动重启策略（`restart: unless-stopped`）
- [ ] 关闭演示验证码：`SMS_MOCK=false` 并接入短信服务
- [ ] 按需配置 HTTPS（有域名后可用 Certbot；海外平台自带证书）
- [ ] 备份策略：MySQL 数据卷定期快照
