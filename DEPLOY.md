# Day-BY-Day 部署指南

项目已按**可上线模式**开发（配置、Docker 镜像、部署脚本均按生产标准）。支持两条路径：

1. **Docker Compose 一键部署**（任何有 Docker 的服务器）
2. **免备案海外免费平台**（Railway / Render 等，无需 ICP 备案，适合快速上线演示）

## 一、Docker Compose 部署（通用）

```bash
# 1. 拉取代码
git clone <仓库地址> dbd && cd dbd

# 2. 一键启动（首次构建约 3-5 分钟）
docker compose up -d --build

# 3. 查看状态
docker compose ps

# 4. 查看日志
docker compose logs -f backend
```

访问：
- 前端：`http://服务器IP`（WEB_PORT 默认 80）
- 接口文档：`http://服务器IP/swagger-ui.html`

### 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `WEB_PORT` | 80 | 前端 nginx 端口 |
| `BACKEND_PORT` | 8080 | 后端端口（Swagger 调试用） |
| `MYSQL_PORT` | 3306 | MySQL 映射端口 |
| `REDIS_PORT` | 6379 | Redis 映射端口 |
| `MYSQL_PASSWORD` | 1234 | MySQL root 密码（**上线必须改**） |

示例：

```bash
MYSQL_PASSWORD=强密码 WEB_PORT=80 docker compose up -d --build
```

### 数据持久化

- MySQL 数据卷：`mysql-data`（重建容器不丢数据）
- Redis 为缓存/计数承载，重启后由后端**懒构建**自愈（Feed/GEO/榜单）
- 演示数据在 MySQL 首次初始化时自动灌入（`init.sql`）

### 常用运维

```bash
docker compose logs -f backend      # 后端日志
docker compose restart backend      # 重启后端
docker compose down                 # 停止（保留数据卷）
docker compose down -v              # 停止并删除数据卷（重置全部数据）
```

## 二、免费服务器部署（免备案，快速演示）

> 国内提供公开 UGC 社区服务需 ICP 备案（个人难办理）。海外免费层**无需备案**，适合快速上线演示。

### 2.1 Railway（推荐，Docker 原生）

1. 注册 [railway.app](https://railway.app)（GitHub 账号登录）
2. `New Project → Deploy from GitHub repo` 选择本仓库
3. Railway 自动识别 `docker-compose.yml` 或以 dockerfile 部署；本项目的多服务结构建议拆分为两个 Service：
   - **web**：根目录 Dockerfile（前端）——实际为 dbd-web/Dockerfile，需设置 Service Root Directory 为 `dbd-web`，并添加环境变量
   - **backend**：`dbd-server`，Root Directory 为 `dbd-server`，环境变量见下
4. 添加插件：MySQL（Railway 提供托管 MySQL）与 Redis
5. backend 环境变量：

| 变量 | 值 |
|---|---|
| `MYSQL_HOST` | Railway MySQL 的 `MYSQLHOST`（插件连接信息里复制） |
| `MYSQL_PORT` | 对应 `MYSQLPORT` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 插件提供的账号密码 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 插件的 `REDISHOST` / `REDISPORT` |

6. 初始化数据库：在 Railway MySQL 上执行 `dbd-server/src/main/resources/db/init.sql`（Railway 控制台提供在线 SQL 客户端）
7. 前端 nginx 反代指向 Railway 内部 backend 域名（修改 `dbd-web/nginx.conf` 的 `proxy_pass http://backend:8080;` 为 Railway 分配的后端域名，或改用 Vite 环境变量指向后端公网地址）

### 2.2 Render

1. 注册 [render.com](https://render.com)
2. 后端：`New → Web Service` 选仓库，Root Directory `dbd-server`，Docker 构建，环境变量同 2.1 表
3. 前端：`New → Static Site`，Build Command `npm run build`、Publish Directory `dist`（Root Directory `dbd-web`），并把 `src/utils/request.js` 的 baseURL 改为后端公网地址
4. Render 免费 Redis 实例（Free 档）+ MySQL 可用 Render 的 PostgreSQL 替代（需调整驱动，不推荐）→ 更简单的组合是 **Railway（MySQL+Redis）** 或外接免费 MySQL（如 PlanetScale / TiDB Cloud Serverless）

### 2.3 Oracle Cloud 永久免费 VPS

1. 申请 Always Free 档 VM（ARM 2 核 12G 或 AMD 2 台 1G）
2. 安装 Docker：`curl -fsSL https://get.docker.com | sh`
3. 克隆仓库 → `docker compose up -d --build`
4. 云防火墙（Security List）放行 80 端口
5. 资源足、无流量费，但账号有回收风险（长期不用会回收实例）

## 三、非 Docker 手工部署（nginx + jar）

```bash
# 1. 数据库
mysql -u root -p < dbd-server/src/main/resources/db/init.sql

# 2. 后端
cd dbd-server && mvn -DskipTests package
nohup java -jar target/dbd-server.jar > run.log 2>&1 &
# 环境变量：MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MYSQL_PASSWORD/REDIS_HOST/REDIS_PORT

# 3. 前端
cd dbd-web && npm install && npm run build
# 将 dist/ 拷贝到 nginx html/，并应用 dbd-web/nginx.conf（proxy_pass 改为 127.0.0.1:8080）
```

## 四、合规说明（已知约束）

- 国内提供公开 UGC 评论/社区服务需 **ICP 备案**（经营性还需 ICP 许可证），个人办理周期长
- 本项目的部署路径：
  - **免备案演示**：海外免费层（Railway/Render/Oracle Cloud）
  - **国内正式上线**：备案后使用腾讯云/阿里云轻量服务器 + Docker Compose（部署方式不变）
- 演示模式验证码（`app.sms.mock=true`）：生产环境需改为 `false` 并接入短信服务商（`AuthServiceImpl` 中已预留切换点）

## 五、上线前检查清单

- [ ] 修改 `MYSQL_PASSWORD` 为强密码
- [ ] 关闭演示验证码：`app.sms.mock=false` 并接入短信服务
- [ ] 关闭 SQL 打印：`application.yml` 中 `log-impl` 移除、`logging.level.com.dbd` 调为 info
- [ ] 按需配置 HTTPS（Railway/Render 自带证书，自建服务器用 Certbot + nginx）
- [ ] 备份策略：MySQL 数据卷定期快照
