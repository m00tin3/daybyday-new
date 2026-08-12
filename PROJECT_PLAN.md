# 论坛项目计划书（v0.2 · 已按审查意见修订）

> 状态：**开发中**。阶段一、二已完成（认证/帖子/吧/用户/排行/搜索 + 前端全页面）；阶段三（秒杀/Feed/GEO）待开工。
>
> 历史审查决策（v0.1 → v0.2）：① 项目名已定 ② 前端选 Vue3 ③ 秒杀保留 ④ 免费服务器部署 ⑤ 接口文档驱动联调 ⑥ 偏 Redis 实战但要能上线

---

## 1. 项目概述

### 1.1 项目目标
开发一个**贴吧风格论坛社区 Day-BY-Day（简称 DbD）**，核心目的：
- 完整实践黑马点评中的 Redis 实战技术点（缓存三件套、分布式锁、Lua、GEO、BitMap、HyperLogLog、ZSet 时间线等），并迁移到论坛场景
- 产出一个**可部署上线**、可用于简历/面试讲解的完整全栈项目

### 1.2 现有资产
- `tieba-demo/index.html`：贴吧风格静态首页（视觉参考，已完成使命——前端已用 Vue3 重写实现全部页面）
- `dbd-web/`：Vue3 前端工程（✅ 10 个页面全部实现）
- `dbd-server/`：Spring Boot 后端工程（✅ 阶段一/二全部接口）

### 1.3 项目名
- 中文名：**盖楼吧**
- 项目名：**Day-BY-Day**（缩写 **DbD**，仓库名、包名前缀用 `dbd`）
- 命名含义：论坛中回复俗称"盖楼"，"每一层回复都是一块砖"；Day-BY-Day 寓意社区成员日复一日地盖楼、分享与签到

---

## 2. 技术栈（已定）

| 层 | 选型 | 说明 |
|---|---|---|
| 前端 | **Vue3 + Vite + Element Plus** + Pinia + Vue Router + Axios | 已定方案 B |
| 后端 | Spring Boot 3 + MyBatis-Plus | |
| 数据库 | MySQL 8 | 业务数据 |
| 缓存 | Redis 7 | 会话/缓存/计数/排行榜等（本项目核心） |
| 接口文档 | **SpringDoc (Swagger UI)** | 文档驱动联调，前端按文档对接 |
| 部署 | nginx + 免费服务器 | 见 §7 |

---

## 3. 整体架构

```
浏览器
  │
  ▼
nginx ──(静态: /)──► Vue3 构建产物 (dist)
  │
  └──(反向代理: /api)──► Spring Boot 后端 (:8080)
                              ├──► MySQL（帖子/用户/评论等业务数据）
                              └──► Redis（会话/缓存/计数/排行榜等）
```

---

## 4. 前端部分（Vue3）

### 4.1 页面规划（✅ = 已实现；同城/活动为完整 UI + 降级展示，后端就绪即切换真实数据）

| 页面 | 路由 | 说明 | 对应后端接口 | 状态 |
|---|---|---|---|---|
| 首页 | `/` | 帖子列表、热门吧、发现入口 | 帖子列表、热吧榜 | ✅ |
| 帖子详情 | `/post/:id` | 正文 + 楼层评论 + 点赞/收藏 + 回帖 | 帖子详情、评论列表 | ✅ |
| 发帖 | `/post/new` | 选吧 + 编辑器 + 提交 | 发帖 | ✅ |
| 登录/注册 | `/login` | 验证码登录、token 管理 | 登录接口 | ✅ |
| 吧主页 | `/bar/:id` | 吧信息、帖子列表、签到、关注 | 吧信息、签到 | ✅ |
| 个人中心 | `/user/:id` | 我的帖子、收藏、签到日历、关注 | 用户信息、关注 | ✅ |
| 搜索 | `/search` | 关键词搜索 + 热搜词展示 | 搜索、热搜 | ✅ |
| 排行榜 | `/rank` | 热帖榜 / 热吧榜 | 榜单接口 | ✅ |
| 同城 | `/nearby` | 附近帖子（GEO，后端阶段三） | GEO 接口 | ⚙️ 降级 |
| 抢楼/徽章 | `/activity/:id` | 抢楼活动、限量徽章领取（后端阶段三） | 秒杀接口 | ⚙️ 降级 |

### 4.2 前端工程结构
```
dbd-web/
├── src/
│   ├── api/            # Axios 封装 + 按模块的接口定义（对接 Swagger 文档）✅
│   ├── router/         # Vue Router 路由 + 登录守卫 ✅
│   ├── stores/         # Pinia（用户态、token）✅
│   ├── views/          # 页面组件（对应上表）✅ 10 个页面全部实现
│   ├── components/     # 公共组件（PostCard 帖子卡片）✅
│   ├── utils/          # 请求拦截器（401 跳登录）、工具函数 ✅
│   ├── App.vue
│   └── main.js
├── vite.config.js      # dev 代理 /api → localhost:8080
└── package.json
```

### 4.3 与后端对接约定
- 统一 RESTful JSON，接口前缀 `/api`
- 登录态：`Authorization: Bearer <token>`；Axios 响应拦截器统一处理 401 → 跳登录
- **联调流程**：后端启动后 Swagger UI 即文档 → 前端按文档写 `src/api/*` → 直接联调，无需额外接口文档工具
- 开发期 Vite proxy 转发 `/api`；上线期 nginx 反代 `/api`

---

## 5. 后端部分

### 5.1 功能模块与 Redis 技术点映射（全部纳入）
| # | 功能模块 | 论坛场景 | Redis 技术 |
|---|---|---|---|
| 1 | 用户认证 | 验证码注册/登录、token 会话 | String + EX 过期 + 拦截器续期 |
| 2 | 帖子缓存 | 帖子详情/列表/吧信息 | 缓存三件套（穿透/击穿/雪崩）+ 延迟双删 |
| 3 | ID 生成 | 帖子 ID、楼层 ID | 时间戳 + Redis 自增（全局唯一 ID） |
| 4 | 点赞/收藏 | 帖子点赞、收藏 | Set（去重判断）+ 计数 |
| 5 | 排行榜 | 热帖榜、热吧榜、热搜词 | ZSet + 定时重算 |
| 6 | 签到 | 每日签到、连续签到奖励 | BitMap + BITFIELD |
| 7 | UV 统计 | 帖子独立访客、吧 DAU | HyperLogLog |
| 8 | 关注 Feed 流 | 关注的人/吧的新帖时间线 | ZSet 时间线 + 滚动分页 |
| 9 | 同城 | 附近帖子/线下聚会 | GEO |
| 10 | **抢楼/限量徽章（秒杀，必做）** | 抢楼活动、限量称号领取 | **Lua 脚本 + 分布式锁 + 一人一单 + 库存预扣** |
| 11 | 防重复提交 | 发帖/回帖频率控制 | SETNX 分布式锁 |

### 5.2 后端工程结构
```
dbd-server/
├── controller/        # 接口层（RESTful）
├── service/           # 业务层（缓存逻辑、Lua 脚本调用等）
├── mapper/            # MyBatis-Plus
├── entity/ dto/ vo/   # 数据模型
├── config/            # Redis/MyBatis/拦截器/Swagger 配置
├── interceptor/       # 登录态拦截器
├── utils/             # 全局 ID、缓存工具等
└── resources/
    ├── lua/           # Lua 脚本（秒杀预扣、解锁、一人一单）
    └── application.yml
```

### 5.3 数据库表（建表 SQL 初稿）

> 完整可执行文件：`dbd-server/src/main/resources/db/init.sql`（含建库、9 张表、种子数据）。

> 约定：库 `dbd`，字符集 `utf8mb4`；主键均为 BIGINT，由 Redis 全局 ID 生成器（时间戳 + 自增）产生，数据库不依赖 AUTO_INCREMENT；时间字段统一 `created_at` / `updated_at`；`sign`、`uv` 数据由 Redis 承载，不建表（按需归档时再建）。

```sql
CREATE DATABASE IF NOT EXISTS dbd DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dbd;

-- ==================== 用户 ====================
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL COMMENT '用户ID（全局ID生成器）',
  `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号（登录账号）',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname`    VARCHAR(32)  NOT NULL COMMENT '昵称',
  `icon`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `sign_text`   VARCHAR(128) DEFAULT NULL COMMENT '个性签名',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户';

-- ==================== 吧 ====================
CREATE TABLE `bar` (
  `id`           BIGINT       NOT NULL COMMENT '吧ID',
  `name`         VARCHAR(32)  NOT NULL COMMENT '吧名称',
  `description`  VARCHAR(255) DEFAULT NULL COMMENT '吧简介',
  `cover`        VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
  `creator_id`   BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `member_count` INT          NOT NULL DEFAULT 0 COMMENT '关注人数（Redis 为准，异步落库）',
  `post_count`   INT          NOT NULL DEFAULT 0 COMMENT '帖子数（Redis 为准，异步落库）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0封禁',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='吧';

-- ==================== 帖子 ====================
CREATE TABLE `post` (
  `id`           BIGINT       NOT NULL COMMENT '帖子ID',
  `bar_id`       BIGINT       NOT NULL COMMENT '所属吧ID',
  `user_id`      BIGINT       NOT NULL COMMENT '发帖人ID',
  `title`        VARCHAR(64)  NOT NULL COMMENT '标题',
  `content`      MEDIUMTEXT   NOT NULL COMMENT '正文',
  `images`       VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表（JSON数组）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除 2精华',
  `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶 0否 1是',
  `like_count`   INT          NOT NULL DEFAULT 0 COMMENT '点赞数（Redis 为准）',
  `favorite_count` INT        NOT NULL DEFAULT 0 COMMENT '收藏数（Redis 为准）',
  `comment_count` INT         NOT NULL DEFAULT 0 COMMENT '楼层数（Redis 为准）',
  `view_count`   INT          NOT NULL DEFAULT 0 COMMENT '浏览量（Redis 为准）',
  `uv_count`     INT          NOT NULL DEFAULT 0 COMMENT '独立访客数（HyperLogLog，定时落库）',
  `score`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '热度分（ZSet 排行用，定时重算）',
  `last_comment_time` DATETIME DEFAULT NULL COMMENT '最后回复时间（列表排序用）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bar_status_created` (`bar_id`, `status`, `created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB COMMENT='帖子';

-- ==================== 评论/楼层 ====================
CREATE TABLE `comment` (
  `id`         BIGINT       NOT NULL COMMENT '楼层ID（每帖内按 Redis 自增生成楼层号）',
  `post_id`    BIGINT       NOT NULL COMMENT '帖子ID',
  `user_id`    BIGINT       NOT NULL COMMENT '回复人ID',
  `floor_no`   INT          NOT NULL COMMENT '楼层号（1 起，本贴内递增）',
  `content`    VARCHAR(2048) NOT NULL COMMENT '内容',
  `images`     VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表（JSON数组）',
  `parent_id`  BIGINT       DEFAULT NULL COMMENT '楼中楼父楼层ID（NULL=直接回帖）',
  `like_count` INT          NOT NULL DEFAULT 0 COMMENT '点赞数（Redis 为准）',
  `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_floor` (`post_id`, `floor_no`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='评论/楼层';

-- ==================== 点赞 ====================
CREATE TABLE `post_like` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子点赞（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 收藏 ====================
CREATE TABLE `post_favorite` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子收藏（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 关注关系 ====================
CREATE TABLE `follow` (
  `id`             BIGINT   NOT NULL,
  `user_id`        BIGINT   NOT NULL COMMENT '粉丝ID',
  `follow_user_id` BIGINT   DEFAULT NULL COMMENT '关注的用户ID（关注类型为用户时）',
  `follow_bar_id`  BIGINT   DEFAULT NULL COMMENT '关注的吧ID（关注类型为吧时）',
  `follow_type`    TINYINT  NOT NULL COMMENT '类型 1关注用户 2关注吧',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`user_id`, `follow_type`, `follow_user_id`, `follow_bar_id`),
  KEY `idx_target` (`follow_user_id`, `follow_bar_id`)
) ENGINE=InnoDB COMMENT='关注关系（用户-用户 / 用户-吧）';

-- ==================== 秒杀活动（抢楼/限量徽章） ====================
CREATE TABLE `activity` (
  `id`          BIGINT      NOT NULL COMMENT '活动ID',
  `bar_id`      BIGINT      DEFAULT NULL COMMENT '关联吧ID（抢楼活动所在吧）',
  `title`       VARCHAR(64) NOT NULL COMMENT '活动标题',
  `type`        TINYINT     NOT NULL COMMENT '类型 1抢楼 2限量徽章',
  `stock`       INT         NOT NULL COMMENT '库存（徽章数量/楼层上限）',
  `award_desc`  VARCHAR(255) DEFAULT NULL COMMENT '奖励描述',
  `begin_time`  DATETIME    NOT NULL COMMENT '开始时间',
  `end_time`    DATETIME    NOT NULL COMMENT '结束时间',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0未开始 1进行中 2已结束',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`begin_time`, `end_time`)
) ENGINE=InnoDB COMMENT='秒杀活动';

-- ==================== 秒杀订单/领取记录 ====================
CREATE TABLE `activity_order` (
  `id`          BIGINT   NOT NULL COMMENT '订单ID',
  `activity_id` BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态 0待发货/待领取 1已领取 2已取消',
  `order_no`    VARCHAR(32) DEFAULT NULL COMMENT '业务单号',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`) COMMENT '一人一单约束',
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='秒杀订单/领取记录';
```

> 说明：
> - 点赞/收藏/计数类数据以 Redis 为准（Set + 计数 Key），本表用于**异步落库与对账**，保证重启不丢数；
> - `activity_order.uk_activity_user` 唯一索引 + Lua 预扣库存，双保险实现"一人一单"；
> - 关注 Feed 流由 `follow` 表驱动，新帖写入 `dbd:feed:{userId}` ZSet；同城 GEO 数据由 `bar`/`post` 的坐标字段另行扩展（或独立 `nearby_post` 表，阶段三定）。

### 5.4 Redis Key 规范（全量规划，✅ = 已实现，实现统一维护于 `RedisKeyConstants`）

统一前缀 `dbd:`，便于排查与过期管理：

```
# ===== 认证（✅） =====
dbd:verify:code:{phone}           验证码，TTL 5min
dbd:verify:lock:{phone}           防重发锁，TTL 60s（SETNX）
dbd:login:token:{token}           登录会话，值存 userId，TTL 30min（拦截器滑动续期）

# ===== 帖子（✅） =====
dbd:id:post|user|comment|...:{yyyy:MM:dd}   全局 ID 生成器（时间戳+自增）
dbd:post:cache:{postId}           详情缓存（空值防穿透 + 随机 TTL 防雪崩）
dbd:post:cache:{postId}:lock      详情重建互斥锁（防击穿，TTL 10s）
dbd:post:list:home:{page}         首页列表缓存，TTL 60s
dbd:post:like:{postId}            点赞 Set（DB 落库兜底）
dbd:post:favorite:{postId}        收藏 Set（DB 落库兜底）
dbd:post:floor:{postId}           楼层号 INCR
dbd:post:uv:{postId}              独立访客 HyperLogLog
dbd:post:view:{postId}            浏览量 INCR
dbd:repeat:post:{userId}          发帖防重复，TTL 3s（SETNX）
dbd:repeat:comment:{userId}       回帖防重复，TTL 3s（SETNX）

# ===== 吧（✅） =====
dbd:bar:cache:{barId}             吧信息缓存
dbd:bar:member:{barId}            关注人数计数
dbd:sign:{userId}:{yyyyMM}        签到 BitMap
dbd:rank:hot:bar                  热吧榜 ZSet（每 5 分钟重算）
dbd:rank:hot:post                 热帖榜 ZSet（热度=浏览+点赞*2+楼层*4）
dbd:search:hot                    热搜词 ZSet（ZINCRBY）

# ===== 用户/Feed/同城（部分✅） =====
dbd:user:fan:{userId}             粉丝数计数（✅）
dbd:user:cache:{userId}           用户信息缓存（未实现，后续按需）
dbd:feed:user:{userId}            关注 Feed 流 ZSet（阶段三）
dbd:geo:post                      同城 GEO（阶段三）

# ===== 秒杀（阶段三） =====
dbd:seckill:stock:{activityId}    库存预扣
dbd:seckill:order:{activityId}:{userId}   一人一单标记
```

### 5.5 接口文档
- 集成 SpringDoc：`/swagger-ui.html` 自动生成全部接口文档
- 每个接口写清：参数、返回值、鉴权要求（是否需 token）
- 联调以 Swagger 为准，前端 `src/api/` 与后端接口签名一一对应

---

## 6. 分阶段实施路线

| 阶段 | 内容 | 里程碑 | 状态 |
|---|---|---|---|
| 阶段一（基础） | 前后端骨架 + 验证码登录 + 帖子 CRUD + 缓存三件套 + 点赞/收藏 | 全栈跑通：首页/详情/发帖/登录可用 | ✅ 已完成 |
| 阶段二（亮点） | 签到 BitMap + UV 统计 + 热帖/热吧榜 + 热搜 + 吧主页/用户中心 | 榜单与统计上线 | ✅ 已完成 |
| 阶段三（进阶） | **抢楼/徽章秒杀（必做）** + 关注 Feed 流 + 同城 GEO | 高并发场景完整 | |
| 阶段四（收尾） | 打包部署：nginx + 免费服务器 + README + 演示数据 | 可演示、可上线 | |

---

## 7. 部署方案（免费服务器）

### 7.1 部署架构
1. 前端 `dbd-web` 构建 → dist 静态文件 → nginx `html/`
2. 后端 `dbd-server.jar` 运行（`:8080`）
3. nginx：`/` → 静态页面；`/api` → 反向代理 `localhost:8080`
4. MySQL / Redis 同机或容器化运行

### 7.2 免费服务器候选（待选）
| 方案 | 备案要求 | 适合度 |
|---|---|---|
| 海外免费层（Railway / Render / Fly.io 等） | 无需备案 | 快速上线演示 Redis 实战效果 |
| Oracle Cloud 免费 VPS（永久免费档） | 无需备案 | 资源足，但申请门槛/回收风险 |
| 国内云新用户免费试用（腾讯云/阿里云轻量） | **需 ICP 备案** | 正式上线路径，备案周期长 |

### 7.3 合规说明（已知约束，先按此模式做）
- 国内提供公开 UGC 评论/社区服务需 **ICP 备案**（经营性还需 ICP 许可证），个人难以办理
- 本阶段**按可上线模式开发**（架构、配置、部署脚本均按生产标准），实际公网部署优先走**免备案路径**（海外免费层）；国内备案上线作为后续独立步骤，不影响开发

---

## 8. 决策记录与剩余待定项

### 8.1 已定决策（v0.2 审查确认）
- [x] 项目名：Day-BY-Day（DbD）
- [x] 前端：Vue3 + Vite + Element Plus（方案 B）
- [x] 抢楼/限量徽章秒杀：**必做**
- [x] 部署：免费服务器，先按可上线模式开发
- [x] 联调方式：SpringDoc 接口文档驱动，直接前后端联调
- [x] 项目定位：偏 Redis 实战，但要能部署上线

### 8.2 剩余待定（不阻塞开工，可后补）
- [ ] 免费部署平台最终选型（§7.2 三选一，阶段四再定）
- [ ] 代码仓库托管位置（GitHub 私有/公开）
- [ ] 是否需要种子/演示数据（建议要，阶段四加）

---

*v0.2 定稿，等待确认开工。*
