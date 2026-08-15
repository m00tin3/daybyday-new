# Day-BY-Day（盖楼吧）

> 贴吧风格论坛社区，完整实践 **Redis 实战技术点**（缓存三件套、分布式锁、Lua、GEO、BitMap、HyperLogLog、ZSet 时间线等），可一键部署上线，适合简历/面试讲解。

- 中文名：**盖楼吧**（论坛中回复俗称"盖楼"，"每一层回复都是一块砖"）
- 寓意：社区成员日复一日地盖楼、分享与签到

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue3 + Vite + Element Plus + Pinia + Vue Router + Axios |
| 后端 | Spring Boot 3.5 + MyBatis-Plus 3.5.9 |
| 数据库 | MySQL 8（业务数据） |
| 缓存 | Redis 7（会话/缓存/计数/排行榜/秒杀/GEO/Feed，**项目核心**） |
| 接口文档 | SpringDoc（Swagger UI） |
| 部署 | Docker Compose / nginx |

## 架构

```
浏览器
  │
  ▼
nginx ──(静态: /)──► Vue3 构建产物 (dist)
  │
  └──(反向代理: /api)──► Spring Boot 后端 (:8080)
                              ├──► MySQL（帖子/用户/评论等业务数据）
                              └──► Redis（会话/缓存/计数/排行榜/秒杀/GEO/Feed）
```

## 功能与 Redis 技术点

| # | 功能模块 | Redis 技术 |
|---|---|---|
| 1 | 验证码注册/登录、token 会话 | String + EX 过期 + 拦截器滑动续期 |
| 2 | 帖子详情/列表/吧信息缓存 | 缓存三件套（空值防穿透 + 互斥锁防击穿 + 随机 TTL 防雪崩） |
| 3 | 帖子/楼层/订单 ID | 时间戳 + Redis 自增（全局唯一 ID，不依赖 DB 自增） |
| 4 | 帖子点赞/收藏 | Set 判重 + 计数，DB 唯一索引落库兜底 |
| 5 | 热帖榜/热吧榜/热搜词 | ZSet + 定时重算（每 5 分钟） |
| 6 | 每日签到/连续签到 | BitMap + BITFIELD |
| 7 | 帖子独立访客 UV | HyperLogLog |
| 8 | 关注 Feed 流 | ZSet 时间线（推模式写扩散 + 懒构建兜底）+ 滚动分页 |
| 9 | 同城·附近帖子 | GEO（GEORADIUS 距离升序，兼容 Redis 3.2+） |
| 10 | **抢楼/限量徽章秒杀** | **Lua 脚本原子预扣库存 + SETNX 一人一单 + 异步落库失败补偿** |
| 11 | 发帖/回帖防重复提交 | SETNX 分布式锁（3 秒） |

## 快速启动

### 方式一：Docker 一键启动（推荐）

```bash
docker compose up -d --build
```

首次启动会自动建库建表并灌入演示数据。启动完成后：

| 入口 | 地址 |
|---|---|
| 前端页面 | http://localhost |
| 接口文档（Swagger UI） | http://localhost/swagger-ui.html |
| 后端健康检查 | http://localhost/api/health |

> 本机已有 MySQL/Redis 占用端口时：`MYSQL_PORT=3307 REDIS_PORT=6380 WEB_PORT=8090 docker compose up -d --build`

### 方式二：本地开发

环境要求：JDK 17+、Maven 3.9+、Node 18+、MySQL 8、Redis（3.2+）

```bash
# 1. 初始化数据库（建库建表 + 演示数据）
mysql -u root -p < dbd-server/src/main/resources/db/init.sql

# 2. 启动后端（默认 localhost:8080，密码默认 1234 可用 MYSQL_PASSWORD 覆盖）
cd dbd-server && mvn spring-boot:run

# 3. 启动前端（Vite 代理 /api → localhost:8080）
cd dbd-web && npm install && npm run dev
# 打开 http://localhost:5173
```

## 演示账号与演示路径

验证码登录（演示模式验证码固定 `123456`），也可直接用种子账号：

| 账号 | 昵称 | 说明 |
|---|---|---|
| 13800000001 | 北京同城用户 | **推荐**：已关注猫咪吧/干饭魂 + 2 位用户，登录后 Feed 即有内容 |
| 13800000002 | 铲屎官小王 | 猫咪吧活跃用户 |
| 13800000003 | 游戏老玩家 | 游戏攻略吧活跃用户 |
| 13800000004 | 上岸人 | 考研上岸吧活跃用户 |
| 13800000005 | 干饭魂 | 干饭魂吧活跃用户 |
| 13800000006 | 新手上路 | 汽车之家吧活跃用户 |

### 5 分钟演示路线

1. **首页**：帖子列表 + 热吧榜（ZSet 懒构建）
2. **登录**：13800000001 + 验证码 123456
3. **关注动态**（导航「关注」）：ZSet 时间线滚动分页
4. **秒杀**：首页侧栏「🎁 限量徽章」立即抢 → 再抢提示"已抢过"（Lua 一人一单）；「🎯 抢楼活动」抢中显示楼层号
5. **同城**：导航「同城」，默认坐标 (116.397, 39.908) 点「找帖子」→ 按距离升序返回北京种子帖；发帖页可「📍 使用我的位置」
6. **帖子详情**：点赞/收藏（Set）、回帖盖楼（楼层号 Redis INCR）、浏览/UV 计数
7. **吧主页**：签到（BitMap 连续天数）、关注
8. **排行榜 / 搜索**：热帖榜、热搜词（ZSet）
9. **接口文档**：/swagger-ui.html 查看全部接口

## 项目结构

```
├── dbd-web/                    # 前端 Vue3
│   ├── src/api/                # Axios + 按模块接口定义
│   ├── src/views/              # 12 个页面（首页/详情/发帖/登录/吧/用户/搜索/排行/Feed/同城/活动/404）
│   ├── Dockerfile              # 前端镜像（Node 构建 + nginx）
│   └── nginx.conf              # 静态托管 + /api 反代
├── dbd-server/                 # 后端 Spring Boot
│   ├── src/main/java/com/dbd/
│   │   ├── controller/         # 接口层（auth/post/bar/user/rank/feed/activity/nearby/search）
│   │   ├── service/            # 业务层（缓存、Lua 调用、Feed 扩散、GEO）
│   │   ├── mapper/ entity/ dto/ vo/
│   │   ├── interceptor/        # 登录拦截器（读可选登录/写必须登录）
│   │   └── utils/              # 全局 ID 生成器、Key 常量、UserContext
│   ├── src/main/resources/
│   │   ├── lua/seckill.lua     # 秒杀原子脚本（库存预扣 + 一人一单）
│   │   └── db/init.sql         # 建库建表 + 演示数据
│   └── Dockerfile              # 后端镜像（Maven 构建 + JRE）
├── docker-compose.yml          # MySQL 8 + Redis 7 + 后端 + 前端 一键编排
├── API.md                      # 接口约定文档（与 Swagger 一致）
├── PROJECT_PLAN.md             # 项目计划书
└── DEPLOY.md                   # 部署指南（免费服务器/免备案平台）
```

## 核心亮点（面试可讲）

### 秒杀（抢楼/限量徽章）

- **Lua 原子脚本**：库存判断 + 预扣 + 一人一单 SETNX，单次 Redis 往返完成，Redis 单线程执行脚本天然互斥
- **一人一单双保险**：Redis 标记 + DB 唯一索引 `uk_activity_user`
- **异步落库 + 失败补偿**：`@Async` 写订单表，落库失败回滚库存并释放标记
- 并发验证：20 并发同用户抢同一活动 → 恰好 1 成功 + 19 个「已抢过」，库存精确扣减

### 关注 Feed 流

- 推模式**写扩散**：发帖后把 postId 写入关注该作者/该吧的每个粉丝的 ZSet（score=毫秒时间戳）
- 滚动分页：`ZREVRANGEBYSCORE` + lastId 排他游标，多取一条判断 hasMore
- 懒构建兜底：时间线为空时从 follow 表拉取关注对象近期帖子重建（Redis 重启自愈）

### 同城 GEO

- 发帖带坐标 → `GEOADD dbd:geo:post`；查询 `GEORADIUS` 按距离升序返回（Redis 6.2+ 可升级 GEOSEARCH）
- GEO 集合为空时从 DB 带坐标帖子懒构建

### 缓存三件套（帖子详情）

- 空值缓存（防穿透）+ SETNX 互斥锁重建（防击穿）+ 随机 TTL（防雪崩）

## 接口文档

- 运行中：`/swagger-ui.html`（SpringDoc 自动生成）
- 静态约定：`API.md`（统一响应 `{code, msg, data}`、鉴权规则、错误码表、全部接口签名）

## 部署

见 [DEPLOY.md](DEPLOY.md)（Docker 部署 + 免费服务器/免备案平台指南 + 合规说明）。

## 项目状态

- ✅ 阶段一：认证/帖子/吧/用户/排行/搜索 + 缓存三件套
- ✅ 阶段二：签到 BitMap + UV 统计 + 热帖/热吧榜 + 热搜
- ✅ 阶段三：抢楼/徽章秒杀（Lua）+ 关注 Feed 流 + 同城 GEO
- ✅ 阶段四：演示数据 + Docker 化部署 + README
