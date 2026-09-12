# Day-BY-Day（DbD）后端接口设计文档

> 用途：后端编码的唯一接口依据（`controller` 签名、`VO` 字段、返回码均以此为准）。
> 运行期文档由 SpringDoc 自动生成（`/swagger-ui.html`），本文件是静态约定，二者冲突时以本文件为准。
> 对应计划书：PROJECT_PLAN.md §5.1（功能-Redis 映射）、§5.4（Key 规范 `dbd:`）。

---

## 1. 通用约定

### 1.1 基础信息
| 项 | 约定 |
|---|---|
| Base URL（开发） | `http://localhost:8080/api`（Vite 代理已配置 `/api`） |
| Base URL（生产） | `http://localhost/api`（nginx 反代 `/api` → `127.0.0.1:8080`） |
| 编码 | UTF-8，`Content-Type: application/json` |
| 时间 | 返回 `yyyy-MM-dd HH:mm:ss` 字符串（VO 用 `String` 或序列化格式统一） |
| 数值 | 所有 ID 为 BIGINT，由后端 Redis 全局 ID 生成器生成，**前端只回传、不生成** |
| JSON 字段 | camelCase（`createdAt`）；数据库 snake_case 由 ORM 映射 |

### 1.2 统一响应包装
所有接口返回：

```json
{ "code": 1, "msg": "ok", "data": { } }
```

- `code = 1` 成功；`code != 1` 失败（`msg` 携带给用户展示的错误信息）
- HTTP 状态码：业务失败仍返回 `200 + code != 1`；**鉴权失败返回 HTTP 401**（前端响应拦截器据此跳登录）

### 1.3 鉴权方式
- 登录后返回 `token`，前端后续请求携带请求头：
  ```
  Authorization: Bearer <token>
  ```
- **拦截器规则（当前实现）**：
  - GET 读请求**可选登录**：带有效 token 则解析出当前用户（请求级状态 `isLiked`/`isFollowed`/`signedToday` 才生效），不带则以匿名身份放行（论坛"读公开"语义）
  - POST 等写操作**必须登录**：token 命中即续期（滑动过期 30 分钟），否则返回 HTTP 401
- 标记 🔒 的接口为写操作或需本人信息，必须携带有效 token
- 特例：`GET /api/auth/me` 虽为 GET，但未登录时由 Controller 手动返回 HTTP 401（登录态信息接口）
- 特例：`GET /api/feed` 虽为 GET，但需登录，未登录时由 Service 返回 code 2003（关注流为个人信息）

### 1.4 分页
请求参数统一 `page`（从 1 开始）、`size`（默认 10，最大 50）。
分页响应 data 结构：

```json
{ "list": [ ], "total": 100, "page": 1, "size": 10 }
```

### 1.5 错误码表
| HTTP | code | 场景 | 前端行为 |
|---|---|---|---|
| 200 | 1 | 成功 | 正常 |
| 200 | 0 | 通用业务失败（msg 说明） | 弹 msg |
| 401 | -1 | 未登录 / token 过期 | 清登录态跳 /login |
| 200 | 2001 | 参数错误 | 弹 msg |
| 200 | 2002 | 资源不存在（帖子/吧/活动） | 弹 msg |
| 200 | 2003 | 无权限（非本人操作等） | 弹 msg |
| 200 | 3001 | 验证码错误/过期 | 弹 msg |
| 200 | 3002 | 操作过于频繁（防重复提交） | 弹 msg |
| 200 | 4001 | 秒杀未开始 / 已结束 | 弹 msg |
| 200 | 4002 | 秒杀库存不足（已售罄） | 弹 msg |
| 200 | 4003 | 已抢过/已领取（一人一单） | 弹 msg |
| 200 | 4004 | 今日已签到 | 弹 msg |

---

## 2. 数据模型（VO）

### 2.1 UserVO 用户
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 用户ID |
| nickname | String | 昵称 |
| icon | String | 头像 URL（可空） |
| signText | String | 个性签名（可空） |
| role | Integer | 角色：0 普通用户 / 1 管理员（前端据此显示管理入口） |
| createdAt | String | 注册时间 |

> 不返回 phone（脱敏）。`auth/login` 等返回的 userInfo 即 UserVO。
> UserVO 会被用于展示**其他用户**（帖子作者、楼层作者），因此不能携带手机号。

### 2.1.1 UserSelfVO 本人资料
`GET /api/auth/me` 返回本 VO：字段与 UserVO 相同，**额外包含 `phone`**。

| 字段 | 类型 | 说明 |
|---|---|---|
| （同 UserVO） | | |
| phone | String | 登录账号（手机号或管理员标识）——**仅本人可见**，用于个人资料页回显 |

> 单独定义该 VO 的原因：资料修改需要回显当前账号，但 UserVO 面向他人展示，
> 若在其中加 phone 会造成手机号泄露。

### 2.2 BarVO 吧
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 吧ID |
| name | String | 吧名称 |
| description | String | 简介（可空） |
| cover | String | 封面 URL（可空） |
| memberCount | Long | 关注人数（Redis 计数） |
| postCount | Long | 帖子数 |
| status | Integer | 状态：1 正常 / 0 已隐藏（仅管理后台返回并筛选，前台只返回正常吧） |
| isFollowed | Boolean | 当前登录用户是否已关注（未登录 false） |
| signedToday | Boolean | 今天是否已签到（未登录 false） |

### 2.3 PostVO 帖子
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 帖子ID |
| barId | Long | 所属吧ID |
| barName | String | 吧名称（冗余，列表展示） |
| author | UserVO | 作者（含 id/nickname/icon） |
| title | String | 标题 |
| content | String | 正文（列表接口只返回截断摘要，详情接口返回全文） |
| images | String[] | 图片 URL 列表 |
| isTop | Boolean | 是否置顶 |
| status | Integer | 1 正常 0 删除 2 精华 |
| likeCount / favoriteCount / commentCount / viewCount | Long | 计数（Redis） |
| uvCount | Long | 独立访客数（HyperLogLog，可选展示） |
| isLiked / isFavorited | Boolean | 当前用户点赞/收藏状态（未登录 false） |
| createdAt | String | 发布时间 |

### 2.4 CommentVO 楼层
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 楼层ID |
| postId | Long | 帖子ID |
| author | UserVO | 回复人 |
| floorNo | Integer | 楼层号（1 起，帖子内递增） |
| content | String | 内容 |
| images | String[] | 图片 URL 列表 |
| parentId | Long | 楼中楼父楼层ID（null=直接回帖） |
| likeCount | Long | 点赞数 |
| createdAt | String | 时间 |

### 2.5 ActivityVO 活动
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 活动ID |
| barId | Long | 关联吧ID（可空） |
| title | String | 标题 |
| type | Integer | 1 抢楼 2 限量徽章 |
| stock | Integer | 总库存 |
| remainStock | Integer | 剩余库存（Redis 实时） |
| awardDesc | String | 奖励描述 |
| beginTime / endTime | String | 起止时间 |
| status | Integer | 0 未开始 1 进行中 2 已结束 |
| grabbed | Boolean | 当前用户是否已抢/已领（未登录 false） |

### 2.6 ActivityOrderVO 订单
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 订单ID |
| activityId | Long | 活动ID |
| userId | Long | 用户ID |
| status | Integer | 0 待领取 1 已领取 2 已取消 |
| createdAt | String | 下单时间 |

---

## 3. 接口明细

### 3.1 认证模块 `auth`（对应前端 `src/api/auth.js`）

#### 3.1.1 发送验证码 🔒(无)
`POST /api/auth/code`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| phone | String | 是 | 11 位手机号（JSON body 字段） |

- 成功：`{ "code": 1, "msg": "验证码已发送", "data": null }`
- Redis：`dbd:verify:code:{phone}`，TTL 5 分钟；`dbd:verify:lock:{phone}` SETNX 限制 60 秒内重复发送 → code 3002
- **演示模式**：`app.sms.mock=true`（默认）时验证码固定 `123456`，不接真实短信通道，联调/演示直接输入即可；生产模式改为 false 并接入短信服务商
- 失败：手机号不合法 → 2001

#### 3.1.2 验证码登录/注册
`POST /api/auth/login`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| phone | String | 是 | 手机号 |
| code | String | 是 | 验证码 |
| nickname | String | 否 | 首次登录自动注册时若未传，后端随机生成 |

- 成功：`{ "code": 1, "msg": "ok", "data": { "token": "xxxx", "userInfo": { UserVO } } }`
- 说明：手机号未注册则自动注册（验证码登录模式，昵称不传则后端生成「用户+尾号」）；已注册直接登录
- Redis：`dbd:login:token:{token}`（token 为 UUID，值为 userId，TTL 30 分钟，拦截器滑动续期）；验证码一次性使用，登录成功后即删
- 失败：验证码错误/过期 → 3001

#### 3.1.3 注册（显式注册，可选实现）
`POST /api/auth/register`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| phone | String | 是 | 手机号 |
| code | String | 是 | 验证码 |
| nickname | String | 是 | 昵称（2-16 字符） |

- 成功：`{ "code": 1, "msg": "ok", "data": { "token": "xxxx", "userInfo": { UserVO } } }`
- 失败：手机号已注册 → 0（msg「该手机号已注册」）；验证码错误 → 3001

#### 3.1.4 当前登录用户信息 🔒（特例：GET 接口未登录也返回 401）
`GET /api/auth/me`
- 成功：`{ "code": 1, "msg": "ok", "data": { UserVO } }`
- 失败：HTTP 401（未登录）

---

### 3.2 帖子模块 `post`（对应前端 `src/api/post.js`）

#### 3.2.1 帖子列表
`GET /api/post/list`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| barId | Long | 否 | 指定吧内帖子 |
| userId | Long | 否 | 指定用户的帖子 |
| keyword | String | 否 | 关键词（简单 LIKE，复杂检索阶段二做热搜即可） |
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

- 成功：`{ "code": 1, "data": { "list": [ PostVO ], "total": Long, "page": 1, "size": 10 } }`
- 排序：置顶优先 → `lastCommentTime` 倒序（首页）；吧内同规则
- Redis：首页（无筛选条件）走列表缓存 `dbd:post:list:home:{page}`（TTL 60 秒），发帖后删除；带筛选条件直查 MySQL。生产可升级"延迟双删"保证一致性
- 失败：无 → 空列表

#### 3.2.2 帖子详情
`GET /api/post/{id}`
- 成功：`{ "code": 1, "data": { PostVO 全文 } }`；浏览数 +1（`dbd:post:uv:{id}` HyperLogLog 记 UV、`dbd:post:view:{id}` INCR 计数，命中缓存时实时覆盖返回）
- Redis：`dbd:post:cache:{id}` 缓存三件套（**空值缓存防穿透** + **互斥锁重建防击穿** + **随机 TTL 防雪崩**，实现见 PostServiceImpl）
- 失败：不存在/已删除 → 2002

#### 3.2.3 发帖 🔒
`POST /api/post`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| barId | Long | 是 | 所属吧ID |
| title | String | 是 | 标题（1-64 字符） |
| content | String | 是 | 正文（1-50000 字符） |
| images | String[] | 否 | 图片 URL 列表 |
| x | Double | 否 | 经度（可选，带坐标则写入 GEO 同城） |
| y | Double | 否 | 纬度（可选，带坐标则写入 GEO 同城） |

- 成功：`{ "code": 1, "msg": "发布成功", "data": { "id": Long } }`
- Redis：ID 生成（`dbd:id:post:{yyyy:MM:dd}` 时间戳 + 自增）；防重复提交 `SETNX dbd:repeat:post:{userId}`（3 秒，先校验后上锁）→ 3002；发布后删除列表缓存；带 x/y 时 `GEOADD dbd:geo:post`；同步触发 Feed 写扩散（推送给关注该作者/该吧的粉丝）
- 失败：参数不合法 → 2001；吧不存在 → 2002

#### 3.2.4 点赞/取消点赞 🔒
`POST /api/post/{id}/like`
- 成功：`{ "code": 1, "data": { "isLiked": true, "likeCount": 12 } }`（幂等：已赞则取消，未赞则点赞）
- Redis：`dbd:post:like:{id}` Set（SISMEMBER 判断 + SCARD 计数）为准；**同步落库** `post_like` 表兜底（唯一索引 `uk_post_user` 防重）
- 失败：帖子不存在 → 2002

#### 3.2.5 收藏/取消收藏 🔒
`POST /api/post/{id}/favorite`
- 成功：`{ "code": 1, "data": { "isFavorited": true, "favoriteCount": 5 } }`（幂等切换）
- Redis：`dbd:post:favorite:{id}` Set 为准；**同步落库** `post_favorite` 表兜底（唯一索引防重）

#### 3.2.6 楼层列表
`GET /api/post/{id}/comments`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10（最大 50） |

- 成功：`{ "code": 1, "data": { "list": [ CommentVO 按 floorNo 升序 ], "total": Long, "page": 1, "size": 10 } }`
- 楼中楼：**当前版本仅返回直接楼层**（`parentId = null`）；楼中楼回复已落库（`parentId` 非空），嵌套组装（replies 前 5 条 + replyCount）待迭代实现

#### 3.2.7 回帖/盖楼 🔒
`POST /api/post/{id}/comment`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| content | String | 是 | 内容（1-2048 字符） |
| parentId | Long | 否 | 楼中楼父楼层ID |

- 成功：`{ "code": 1, "msg": "盖楼成功", "data": { "id": Long, "floorNo": 3 } }`
- Redis：楼层号 = `INCR dbd:post:floor:{postId}`（同 `comment.floor_no`）；防重复提交 `SETNX dbd:repeat:comment:{userId}`（3 秒，先校验后上锁）→ 3002；帖子 `commentCount` +1；更新 `lastCommentTime`；同步落库；删除详情/列表缓存
- 失败：帖子不存在 → 2002；父楼层不存在 → 2002

---

### 3.3 吧模块 `bar`（对应前端 `src/api/bar.js`）

#### 3.3.1 吧信息
`GET /api/bar/{id}`
- 成功：`{ "code": 1, "data": { BarVO } }`
- Redis：`dbd:bar:cache:{id}` 缓存三件套；memberCount/postCount 取 Redis 计数
- 失败：不存在 → 2002

#### 3.3.2 吧内帖子
`GET /api/bar/{id}/posts`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

- 成功：同 3.2.1 分页结构（PostVO 列表）

#### 3.3.3 吧签到 🔒
`POST /api/bar/{id}/sign`
- 成功：`{ "code": 1, "msg": "签到成功", "data": { "signedDays": 5, "signCount": 3, "award": "..." } }`
  - `signCount`：本月累计签到天数；`signedDays`：连续签到天数；连续 7 天可发奖励（可空实现）
- Redis：BitMap `dbd:sign:{userId}:{yyyyMM}` + BITFIELD 查询；今日已签 → 4004
- 失败：未登录 401；吧不存在 2002

#### 3.3.4 关注/取消关注吧 🔒
`POST /api/bar/{id}/follow`
- 成功：`{ "code": 1, "data": { "isFollowed": true, "memberCount": 1024 } }`（幂等切换）
- Redis：关注关系写 `follow` 表（type=2）+ `dbd:bar:member:{id}` 计数；Feed 流（见 3.5.5 说明）

#### 3.3.5 热吧榜
`GET /api/bar/rank`
- 成功：`{ "code": 1, "data": [ BarVO（按 memberCount 降序，最多 10） ] }`
- Redis：ZSet `dbd:rank:hot:bar`（score=memberCount，定时重算）

---

### 3.4 用户模块 `user`（对应前端 `src/api/user.js`）

#### 3.4.1 用户主页信息
`GET /api/user/{id}`
- 成功：`{ "code": 1, "data": { "user": UserVO, "postCount": Long, "followerCount": Long, "followingCount": Long, "isFollowed": Boolean } }`
- Redis：粉丝数 `dbd:user:fan:{id}`（String 计数，无值 0）；关注数（followingCount）直查 follow 表；当前实现未做用户缓存

#### 3.4.2 用户帖子
`GET /api/user/{id}/posts`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

- 成功：分页结构（PostVO）

#### 3.4.3 我的收藏 🔒
`GET /api/user/favorites`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

- 成功：分页结构（PostVO 列表，仅本人可查：未登录/非本人 → 2003）

#### 3.4.4 签到日历
`GET /api/user/{id}/sign`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| month | String | 否 | `yyyyMM`，默认当月 |

- 成功：`{ "code": 1, "data": { "yearMonth": "202608", "signedDays": 12, "signList": [1, 3, 5, 8], "signCount": 12 } }`
  - `signList`：当月已签到的日期号（1-31）
- Redis：BitMap `dbd:sign:{userId}:{yyyyMM}`，BITFIELD 读取 31 位一次性返回

#### 3.4.5 关注/取消关注用户 🔒
`POST /api/user/{id}/follow`
- 成功：`{ "code": 1, "data": { "isFollowed": true, "followerCount": 66 } }`（幂等切换）
- Redis：`follow` 表（type=1）为准 + 粉丝计数 `dbd:user:fan:{id}`（INCR/DECR）；不可关注自己 → 2003

#### 3.4.6 修改个人资料 🔒
`PUT /api/user/profile`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| nickname | String | 否 | 1-32 字 |
| signText | String | 否 | ≤128 字 |
| icon | String | 否 | 头像 URL，≤255 字符（项目暂无文件上传/对象存储，故填写图片地址） |
| phone | String | 否 | 登录账号，6-20 位数字，需全局唯一 |

- 成功：`{ "code": 1, "msg": "资料已更新", "data": UserSelfVO }`
- **null 字段表示不修改**（MyBatis-Plus `updateById` 默认忽略 null），因此支持只提交需要改的字段
- 失败：2001 —— 昵称为空/超长、签名超长、账号格式错误、**账号已被占用**
- 安全：userId 一律取自登录态 `UserContext`，不接受请求体传入，因此只能改本人资料
- 唯一性：先查冲突返回友好提示，再由 `user.uk_phone` 唯一索引兜底防并发

---

### 3.5 排行/Feed 模块 `rank`

#### 3.5.1 热帖榜
`GET /api/rank/hot/post`
- 成功：`{ "code": 1, "data": [ PostVO（按热度降序，最多 20） ] }`
- Redis：ZSet `dbd:rank:hot:post`，score=热度分 = 浏览 + 点赞×2 + 楼层×4（浏览/点赞实时取 Redis 计数），@Scheduled 每 5 分钟重算 + 首次访问懒构建

#### 3.5.2 关注 Feed 流 🔒
`GET /api/feed`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| lastId | Long | 否 | 滚动分页游标（上一页最后一条的 score） |
| size | Integer | 否 | 默认 10 |

- 成功：`{ "code": 1, "data": { "list": [ PostVO ], "lastId": Long, "hasMore": true } }`
- Redis：`dbd:feed:user:{userId}` ZSet（score=发帖时间戳毫秒）；发帖时推模式写扩散（推送给关注该作者/该吧的粉丝）；feed 为空时从 follow 表拉取关注对象近期帖子懒构建兜底；滚动分页 `ZREVRANGEBYSCORE`（lastId 排他游标，多取一条判断 hasMore）
- 失败：未登录 → 2003（🔒 特例：GET 但需登录，见 §1.3）

---

### 3.6 秒杀模块 `activity`（对应前端 `src/api/activity.js`）

#### 3.6.1 活动详情
`GET /api/activity/{id}`
- 成功：`{ "code": 1, "data": { ActivityVO } }`
- Redis：`dbd:seckill:stock:{id}` 剩余库存实时读取

#### 3.6.2 抢楼/领取徽章 🔒
`POST /api/activity/{id}/grab`
- 成功：`{ "code": 1, "msg": "抢楼成功", "data": { "orderId": Long, "floorNo": Integer(抢楼时返回) } }`
- **实现要点（面试核心）**：
  1. Lua 原子脚本（`resources/lua/seckill.lua`）：库存判断 + `dbd:seckill:stock:{id}` 预扣 + `dbd:seckill:order:{id}:{userId}` SETNX 一人一单，单次 Redis 往返（Redis 单线程执行脚本，天然互斥，无需额外分布式锁）
  2. 成功后 `@Async` 异步写 `activity_order` 表（唯一索引 `uk_activity_user` 双保险），主流程立即返回
  3. 主流程不建表事务：Lua 通过 → 异步落库；落库失败补偿回滚（库存 +1 + 删除一人一单标记）
  4. 库存预热：`SETNX dbd:seckill:stock:{id}` 首次访问时从 DB stock 初始化；抢楼楼层号 = stock - remainStock
- 失败：未开始/已结束 → 4001；售罄 → 4002；重复抢 → 4003

---

### 3.7 搜索模块 `search`

#### 3.7.1 热搜词
`GET /api/search/hot`
- 成功：`{ "code": 1, "data": [ "考研", "猫咪", "..." ] }`（最多 10 个）
- Redis：ZSet `dbd:search:hot`，score=搜索次数；搜索接口命中时 ZINCRBY 加 1

#### 3.7.2 搜索帖子
`GET /api/search/post`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | 是 | 关键词 |
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

- 成功：分页结构（PostVO）
- Redis：搜索词 ZINCRBY 记录热搜；结果可走 MySQL LIKE（规模小够用）

---

### 3.8 同城模块 `nearby`（对应前端 `src/api/activity.js`）

#### 3.8.1 附近帖子
`GET /api/nearby/post`
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| x | Double | 是 | 经度 |
| y | Double | 是 | 纬度 |
| distance | Integer | 否 | 半径（米），默认 5000 |

- 成功：`{ "code": 1, "data": [ { PostVO, "distance": 1234.5 } ] }`（按距离升序）
- Redis：GEO `dbd:geo:post`，发帖带坐标时 GEOADD；查询 GEORADIUS（兼容 Redis 3.2+，6.2+ 可等价升级 GEOSEARCH）；GEO 集合为空时从 DB 带坐标帖子懒构建

---

### 3.9 管理模块 `admin`（对应前端 `src/api/admin.js`）

> **整个 `/api/admin/**` 由 `AdminInterceptor` 强制要求 role=1**：
> 未登录 → HTTP 401 `{"code":-1,"msg":"未登录"}`；
> 已登录但非管理员 → HTTP 403 `{"code":2003,"msg":"需要管理员权限"}`。
>
> 与登录拦截器的差异：登录拦截器对 GET 是"可选登录"（匿名放行，论坛读公开），
> 而管理接口即使是 GET 也必须登录且为管理员。

**术语区分**

| 操作 | 语义 | 可恢复 |
|---|---|---|
| 隐藏 | 只改状态（帖子 `status=3` / 吧 `status=0`），前台全链路不可见 | ✅ 可恢复 |
| 删除 | 物理 `DELETE`，并级联清理关联数据与 Redis 残留 | ❌ 不可恢复 |

#### 3.9.1 帖子管理列表 🔒管理员
`GET /api/admin/post/list`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | 否 | 匹配标题或正文 |
| status | Integer | 否 | 1 正常 / 2 精华 / 3 隐藏 / 0 已删除；不传则全部 |
| page / size | Integer | 否 | 默认 1 / 10 |

- 成功：分页结构（PostVO，**含隐藏等前台不可见的状态**）
- 与前台 `/api/post/list` 的关键区别：前台 SQL 固定 `WHERE p.status IN (1,2) AND b.status = 1`

#### 3.9.2 隐藏帖子 🔒管理员
`POST /api/admin/post/{id}/hide`
- 成功：`{ "code": 1, "msg": "已隐藏" }`
- 语义：`status → 3`，并清除帖子详情缓存、重建互斥锁与首页列表缓存
- 重复隐藏 → 2001

#### 3.9.3 恢复帖子 🔒管理员
`POST /api/admin/post/{id}/restore`
- 成功：`{ "code": 1, "msg": "已恢复" }`
- 语义：`status → 1`（**原"精华"标记不保留**）

#### 3.9.4 删除帖子 🔒管理员
`DELETE /api/admin/post/{id}`
- 成功：`{ "code": 1, "msg": "已删除" }`
- 级联清理 DB：`comment` / `post_like` / `post_favorite` 中该帖的全部记录
- 清理 Redis：`dbd:post:cache:{id}` 与 `:lock`、`dbd:post:like|favorite|floor|view|uv:{id}`、
  `dbd:geo:post`（ZREM）、`dbd:rank:hot:post`（ZREM）、全部 `dbd:feed:user:*`（**SCAN + ZREM，不用 KEYS**）、首页列表缓存

#### 3.9.5 吧管理列表 🔒管理员
`GET /api/admin/bar/list`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | 否 | 匹配吧名称 |
| status | Integer | 否 | 1 正常 / 0 已隐藏；不传则全部 |
| page / size | Integer | 否 | 默认 1 / 10 |

#### 3.9.6 创建贴吧 🔒管理员
`POST /api/admin/bar`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String | 是 | 1-32 字，全局唯一 |
| description | String | 否 | ≤255 字 |
| cover | String | 否 | 封面 URL，≤255 字符 |

- 成功：`{ "code": 1, "msg": "创建成功", "data": { "id": 636654650237386753 } }`
- 新吧 ID 由 Redis 全局 ID 生成器分配，`creator_id` 记录管理员，初始状态正常
- 名称重复 → 2001「该吧名称已存在」（`bar.uk_name` 唯一索引兜底）

#### 3.9.7 隐藏 / 恢复贴吧 🔒管理员
`POST /api/admin/bar/{id}/hide` / `POST /api/admin/bar/{id}/restore`
- `status → 0` / `status → 1`
- 隐藏时额外：清除吧信息缓存、从热吧榜 ZSet 移除、清首页列表缓存
- 隐藏后其下帖子在**列表 SQL（`b.status = 1`）、帖子详情、Feed 流、热帖榜**中一并不可见

#### 3.9.8 删除贴吧 🔒管理员
`DELETE /api/admin/bar/{id}`
- 成功：`{ "code": 1, "msg": "已删除" }`
- **完整级联清理**（避免留下任何指向已删吧的孤儿数据）：
  1. 该吧下全部帖子 —— 每篇再走 3.9.4 的完整清理（楼层/点赞/收藏 + Redis 残留）
  2. `follow` 中 `follow_bar_id = 该吧` 的关注关系 —— 吧已不存在，这些记录已无意义
  3. `activity` 中 `bar_id = 该吧` 的秒杀活动，连同其 `activity_order` 订单记录
  4. 上述活动的 Redis 键：`dbd:seckill:stock:{activityId}` 与全部
     `dbd:seckill:order:{activityId}:*`（**SCAN 匹配，不用会阻塞 Redis 的 KEYS**）
  5. 吧自身相关：吧信息缓存、`dbd:bar:member:{id}`、热吧榜 ZSet 成员、首页列表缓存
- 缺少第 3 步时，首页「限量徽章 / 抢楼」入口会继续指向一个所属吧已不存在的活动

---

## 4. 接口与前端 api/ 对照表

| 前端函数 | 路径 | 方法 | 鉴权 |
|---|---|---|---|
| `auth.sendCode` | /api/auth/code | POST | - |
| `auth.loginByCode` | /api/auth/login | POST | - |
| `auth.register` | /api/auth/register | POST | - |
| `auth.getUserInfo` | /api/auth/me | GET | 🔒（特例：未登录返回 401） |
| `post.getPostList` | /api/post/list | GET | - |
| `post.getPostDetail` | /api/post/{id} | GET | - |
| `post.createPost` | /api/post | POST | 🔒 |
| `post.likePost` | /api/post/{id}/like | POST | 🔒 |
| `post.favoritePost` | /api/post/{id}/favorite | POST | 🔒 |
| `post.getComments` | /api/post/{id}/comments | GET | - |
| `post.addComment` | /api/post/{id}/comment | POST | 🔒 |
| `bar.getBarInfo` | /api/bar/{id} | GET | - |
| `bar.getBarPosts` | /api/bar/{id}/posts | GET | - |
| `bar.signIn` | /api/bar/{id}/sign | POST | 🔒 |
| `bar.followBar` | /api/bar/{id}/follow | POST | 🔒 |
| `bar.getBarRank` | /api/bar/rank | GET | - |
| `user.getUserProfile` | /api/user/{id} | GET | - |
| `user.getUserPosts` | /api/user/{id}/posts | GET | - |
| `user.getUserFavorites` | /api/user/favorites | GET | 🔒 |
| `user.getSignCalendar` | /api/user/{id}/sign | GET | - |
| `user.followUser` | /api/user/{id}/follow | POST | 🔒 |
| `user.updateUserProfile` | /api/user/profile | PUT | 🔒（仅本人） |
| `admin.getAdminPosts` | /api/admin/post/list | GET | 🔒管理员 |
| `admin.hidePost` | /api/admin/post/{id}/hide | POST | 🔒管理员 |
| `admin.restorePost` | /api/admin/post/{id}/restore | POST | 🔒管理员 |
| `admin.deletePost` | /api/admin/post/{id} | DELETE | 🔒管理员 |
| `admin.getAdminBars` | /api/admin/bar/list | GET | 🔒管理员 |
| `admin.createBar` | /api/admin/bar | POST | 🔒管理员 |
| `admin.hideBar` | /api/admin/bar/{id}/hide | POST | 🔒管理员 |
| `admin.restoreBar` | /api/admin/bar/{id}/restore | POST | 🔒管理员 |
| `admin.deleteBar` | /api/admin/bar/{id} | DELETE | 🔒管理员 |
| `user.getHotPosts` | /api/rank/hot/post | GET | - |
| `feed.getFeed` | /api/feed | GET | 🔒（特例：未登录返回 2003） |
| `activity.getActivityInfo` | /api/activity/{id} | GET | - |
| `activity.grabActivity` | /api/activity/{id}/grab | POST | 🔒 |
| `activity.getHotSearch` | /api/search/hot | GET | - |
| `activity.searchPosts` | /api/search/post | GET | - |
| `activity.getNearbyPosts` | /api/nearby/post | GET | - |

---

## 5. 后端实现顺序（对应计划书阶段）

> ✅ = 已完成并提交

| 顺序 | 内容 | 说明 | 状态 |
|---|---|---|---|
| 1 | 骨架：Spring Boot 3 + MyBatis-Plus + 统一响应 + 全局异常 + 拦截器 | 先跑通 401 链路 | ✅ |
| 2 | 认证：验证码登录 + 全局 ID 生成器 | 解锁所有 🔒 接口 | ✅ |
| 3 | 帖子：发帖/列表/详情/楼层（缓存三件套） | 阶段一主线 | ✅ |
| 4 | 点赞/收藏 + 落库兜底 | 阶段一收尾 | ✅ |
| 5 | 吧：吧信息/签到 BitMap/热吧榜 ZSet/关注 | 阶段二 | ✅ |
| 6 | 用户中心 + UV 统计 + 热搜 | 阶段二收尾 | ✅ |
| 7 | 秒杀 Lua 脚本 + Feed 流 + GEO 同城 | 阶段三 | ✅ |
| 8 | 演示数据 + 部署（nginx 已就绪） | 阶段四 | ✅ |
| 9 | 管理后台：管理员角色 + 帖子/吧的隐藏与物理删除 + 创建贴吧 | 阶段五（`user.role` + `/api/admin/**`） | ✅ |
| 10 | 个人资料页：查看 + 修改（昵称/签名/头像/登录账号） | 阶段五（`PUT /api/user/profile`） | ✅ |

> 数据库建表 SQL 见 `dbd-server/src/main/resources/db/init.sql`（库名 `dbd`，9 张表 + 种子数据）。
>
> **存量库升级**：管理功能新增了 `user.role` 字段与帖子 `status=3`（隐藏）语义，
> 已部署过的库需执行一次增量脚本 `dbd-server/src/main/resources/db/migration_admin.sql`
> （非幂等，重复执行会报 Duplicate column，忽略即可）。
