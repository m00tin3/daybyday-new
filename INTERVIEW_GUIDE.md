# Day-BY-Day 简历知识点详解（面试手册）

> 配套文档：README.md（项目介绍）/ API.md（接口约定）/ PROJECT_PLAN.md（计划书）
> 用法：简历上写的每个词，本文档都能找到「原理 + 项目代码位置 + 面试话术」。建议按顺序通读一遍，再用第 8 章的快问快答自测。

---

## 0. 全景图：这个项目的知识地图

一句话：**一个论坛项目，把 Redis 的八种数据结构/玩法全部串了起来**。

```
                         Day-BY-Day 论坛
                              │
   ┌──────────┬──────────┬────┴─────┬──────────┬──────────┐
   │          │          │          │          │          │
 秒杀(核心)  缓存三件套   Feed 流   多维应用    防重/锁    工程化
   │          │          │          │          │          │
 Lua 脚本   穿透/击穿/    ZSet 时间线  BitMap签到  SETNX    Docker
 库存预扣   雪崩        推模式扩散   HyperLogLog 分布式锁   nginx
 一人一单   互斥锁重建   滚动分页    ZSet 榜单   全局ID     SpringDoc
 异步落库   随机 TTL    懒构建兜底   GEO 同城    token会话
 失败补偿   一致性                           (INCR/EXPIRE)
```

面试时先把这张图讲出来（30 秒），面试官立刻知道你的知识体系是成片的，不是背的零散八股。

---

## 1. 秒杀系统（必问，含金量最高）

### 1.1 秒杀到底难在哪？

假设 100 个库存，1000 人同时抢。最朴素的写法：

```java
// ❌ 最蠢写法：查 → 判断 → 扣，三步分开
Integer stock = getStock(activityId);          // 1000 个人都读到 100
if (stock > 0) {                               // 1000 个人都判断通过
    stock = stock - 1;                         // 1000 个人都扣
    updateStock(activityId, stock);            // 最终库存 = 99，但卖出去 1000 单
}
```

**三个问题**：
1. **超卖**：并发下"读-判-写"三步之间有窗口，卖出去的比库存多
2. **重复抢**：同一个人狂点，可能抢到多单
3. **数据库压力**：1000 个请求同时打到 MySQL，连接池打满，正常用户也进不来

### 1.2 演进路线（面试讲这个框架，体现思考过程）

| 版本 | 方案 | 问题 |
|---|---|---|
| v1 | 直接查库扣库存 | 超卖 + DB 压力 |
| v2 | `synchronized` 锁方法 | 单机有效；锁粒度粗，串行化全部请求；多机部署失效 |
| v3 | 数据库悲观锁 `SELECT ... FOR UPDATE` | 把压力全压给 DB，秒杀时 DB 是瓶颈 |
| v4 | 数据库乐观锁 `UPDATE ... WHERE stock > 0` | 比 v3 好，但仍是 DB 扛并发 |
| v5 | **Redis 原子操作** | ✅ 单步操作在 Redis 单线程下天然原子 |
| v6 | **Lua 脚本**（本项目） | ✅ 多步操作合并成一次往返，原子完成 |

### 1.3 为什么 Redis 能保证原子性？

**关键知识点**：Redis 是**单线程**执行命令的（6.0 后网络 IO 多线程，但**命令执行仍是单线程**）。

- 每个命令要么完整执行、要么不执行，执行期间不会有其他命令插进来
- 所以 `INCR`、`DECR`、`SETNX` 天然是原子的：读+改一步完成
- 但**多个命令之间**仍可能被其他客户端的命令插入——这正是需要 Lua 的原因

### 1.4 Lua 脚本：把三步合并成一次往返

**核心思想**：Lua 脚本是**在 Redis 内部执行的**，Redis 执行脚本期间不会执行任何其他命令——相当于给一串命令上了锁。

本项目的脚本 `dbd-server/src/main/resources/lua/seckill.lua`，逐行解读：

```lua
-- KEYS[1] = dbd:seckill:stock:{activityId}       库存
-- KEYS[2] = dbd:seckill:order:{activityId}:{userId}  一人一单标记
-- ARGV[1] = userId
-- 返回：0 成功；1 库存不足；2 已抢过

-- 第 1 步：库存是否存在（未预热视为不可抢）
if redis.call('exists', KEYS[1]) == 0 then
  return 1
end

-- 第 2 步：读库存 + 判断
local stock = tonumber(redis.call('get', KEYS[1]))
if stock <= 0 then
  return 1
end

-- 第 3 步：SETNX 原子标记一人一单（已存在则重复抢）
if redis.call('setnx', KEYS[2], ARGV[1]) == 0 then
  return 2
end

-- 第 4 步：库存 -1
redis.call('decr', KEYS[1])
return 0
```

整个脚本 4 个步骤只花**一次网络往返**、在 Redis 内部一口气执行完。1000 个并发请求的脚本会**排队串行执行**：第 1 个把库存 100→99，第 2 个 99→98……第 100 个 1→0，第 101 个读到 0 直接返回"库存不足"。

> **面试追问：为什么用 Lua，不用 Redis 事务（MULTI/EXEC）？**
> 答：MULTI/EXEC 没有条件分支——它只能把命令排队执行，无法在"库存不足时提前 return 打断"。Lua 可以在脚本里做判断，`return` 不同错误码。而且 Lua 一次往返，事务最少两次（MULTI 和 EXEC）。

调用代码在 `ActivityServiceImpl.grab()`：

```java
Long r = stringRedisTemplate.execute(seckillScript,
        List.of(stockKey, orderKey), String.valueOf(userId));
if (r == 1) throw BusinessException.seckill(4002, "手慢了，已被抢光");
if (r == 2) throw BusinessException.seckill(4003, "你已抢过，不能重复参与");
```

### 1.5 一人一单：Redis 标记 + DB 唯一索引双保险

**为什么需要双保险**：Redis 是缓存，极端情况下（重启且没持久化）标记会丢。所以：

1. **Redis 层**：`SETNX dbd:seckill:order:{activityId}:{userId}` —— 原子标记，并发下只放行一次（20 并发实测：1 成功 19 个 4003）
2. **DB 层**：`activity_order` 表唯一索引 `uk_activity_user(activity_id, user_id)` —— 即使 Redis 标记丢了，DB 插入也会因唯一键冲突失败

### 1.6 异步落库 + 失败补偿（本项目比黑马点评原版更完整的一环）

```
用户请求 → Lua 预扣（Redis，~1ms）→ 立即返回"抢到了"
                              └→ 异步线程写 activity_order 表（几百 ms，用户无感知）
                                   └→ 写失败？回滚：库存 INCR +1、删除一人一单标记（用户可重抢）
```

实现见 `SeckillOrderPersistService`：

```java
@Async   // 启动类加了 @EnableAsync
public void persist(ActivityOrder order, Long activityId, Long userId) {
    try {
        activityOrderMapper.insert(order);
    } catch (Exception e) {
        // 补偿：库存回补 + 释放一人一单标记，允许用户重新抢
        stringRedisTemplate.opsForValue().increment(RedisKeyConstants.SECKILL_STOCK + activityId);
        stringRedisTemplate.delete(RedisKeyConstants.SECKILL_ORDER + activityId + ":" + userId);
    }
}
```

> **面试追问：异步落库失败、补偿也失败怎么办？**
> 诚实回答：本项目用 try-catch 补偿 + DB 唯一索引兜底，能覆盖"Redis 正常、DB 偶发故障"的场景；生产级会引入 MQ 重试 + 对账任务。这样回答既承认边界又展现工程思考。

### 1.7 秒杀常见追问汇总

| 问题 | 答案要点 |
|---|---|
| 库存什么时候写进 Redis？ | 抢购时 `SETNX` 预热：无值才初始化，避免覆盖进行中的实时库存（`grab()` 里） |
| 抢楼活动楼层号怎么算？ | `floorNo = 总库存 - 剩余库存`，即"第几个抢到"；徽章活动不返回楼层 |
| Redis 挂了怎么办？ | 秒杀不可用（fail-fast 返回繁忙）；本项目靠 DB 唯一索引保证即使标记丢失也不超卖 |
| 真到百万并发？ | 诚实说明项目边界：单体 + Redis 已能扛数千 QPS；再往上要 MQ 削峰、读写分离、多级缓存、集群——知道路线但没在本项目做 |

---

## 2. 缓存三件套（穿透 / 击穿 / 雪崩）

**先记结论**：三个词都是"缓存没挡住请求，压力漏到数据库"的场景，区别是**漏的原因不同**。

| 现象 | 比喻 | 漏的原因 | 本项目对策 |
|---|---|---|---|
| 穿透 | 有人专挑不存在的钥匙开锁 | 查的数据 DB 里根本没有 | 空值缓存（5 分钟） |
| 击穿 | 一把很热的钥匙断了，一群人挤门 | 热点 key 过期瞬间大量并发 | SETNX 互斥锁重建 |
| 雪崩 | 整栋楼的钥匙同时过期 | 大量 key 同一时刻过期 | 随机 TTL（10~15 分钟） |

实现都在 `PostServiceImpl.detail()`，走读一下：

### 2.1 穿透：空值缓存

```java
// readCache() 里：缓存的空字符串 = "DB 里没这条数据"
if (cached.isEmpty()) {
    throw BusinessException.notFound("帖子不存在");
}
// writeCache() 里：查不到也缓存，但 TTL 更短（5 分钟）
if (vo == null) {
    stringRedisTemplate.opsForValue().set(key, "", EMPTY_TTL);
}
```

**原理**：恶意请求查不存在的 id，如果不缓存，每次都穿透到 DB；缓存一个"空"（5 分钟），同一 id 的重复攻击就被 Redis 挡住了。5 分钟比正常 TTL 短，是为了让"刚被误判的数据"能尽快恢复（比如先查了详情后帖子才发布）。

> 追问：和布隆过滤器比？空值缓存实现简单但有内存被"垃圾 key"占用的风险；布隆过滤器空间效率高但有误判率且实现复杂。本项目数据量小，空值缓存足够。

### 2.2 击穿：互斥锁重建

```java
// detail() 里：缓存 miss 后，抢锁——只有一个人能去查 DB 重建
if (tryLock(id)) {
    try {
        vo = readCache(id);              // Double Check：拿到锁后再查一次，可能别人已重建
        if (vo == null) {
            vo = buildDetail(id);        // 查 DB
            writeCache(id, vo);          // 写缓存
        }
    } finally {
        unlock(id);
    }
} else {
    sleep(50);                           // 没抢到锁：等 50ms 直查 DB（保可用，不阻塞用户）
    vo = buildDetail(id);
}

// 锁本身：SETNX 占用，10 秒 TTL 防死锁
private boolean tryLock(Long id) {
    return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
            .setIfAbsent(cacheKey + ":lock", "1", LOCK_TTL));
}
```

**两个细节值得在面试里主动讲**：
1. **Double Check**：抢到锁后必须再读一次缓存——可能你排队等锁时，前面的人已经重建好了，你再查库就浪费了
2. **锁要带 TTL**：如果重建线程挂了没释放锁，没 TTL 就是死锁；本项目 10 秒

> 追问：为什么锁不用 Redisson？答：项目规模用 SETNX + TTL 够用且零依赖；Redisson 的看门狗（自动续期）适合重建超过锁 TTL 的慢查询场景，是升级路径。见 §4.3。

### 2.3 雪崩：随机 TTL

```java
// writeCache()：基础 10 分钟 + 0~5 分钟随机
long ttl = CACHE_TTL.toSeconds() + ThreadLocalRandom.current().nextLong(CACHE_TTL_JITTER);
```

**原理**：如果所有帖子缓存都正好 10 分钟过期，每到整点时刻 DB 会被瞬时打满。给每个 key 加随机偏移，过期时间就"错峰"了。

### 2.4 缓存一致性（雪崩之外最常被追问）

本项目的做法：**写完 DB 后删缓存**（旁路缓存模式）：

```java
postMapper.insert(post);
deleteHomeListCache();   // 发帖后删首页列表缓存（1~5 页）
deleteCache(postId);     // 回帖后删详情缓存
```

**面试要能讲出完整一致性阶梯**：
1. 先删缓存再更库 → 期间并发读会写回旧值（脏数据）
2. **先更库再删缓存**（推荐基准）→ 最坏情况是删缓存失败留下旧值
3. **延迟双删** → 更库后删一次，睡 500ms 再删一次，覆盖极端时序窗口（本项目代码注释里明确写了这是升级路径）
4. 终极方案：canal 监听 binlog 异步删缓存 / MQ 保证最终一致

> 追问：删缓存失败怎么办？答：本项目没有重试机制（如实说）；生产会加 MQ 重试或订阅 binlog。**承认边界 + 给出升级路径**是最好的回答方式。

---

## 3. 关注 Feed 流（ZSet 时间线）

### 3.1 需求拆解

用户关注了 2 个人 + 2 个吧，打开"关注动态"页，要看到这些对象发的新帖，按时间倒序，能翻页。

### 3.2 推模式 vs 拉模式 vs 推拉结合（高频考点）

| 模式 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| 推（写扩散） | 发帖时，把帖子 id 写进**每个粉丝**的时间线 | 读时零聚合，快 | 发帖时要写 N 个粉丝；大 V 粉丝百万级会写爆 |
| 拉（读聚合） | 读时，现查所有关注对象的最新帖再合并排序 | 发帖零成本 | 每次读都要聚合，关注多时很慢 |
| **推拉结合** | 普通用户推，大 V 拉（读时合并大 V 的最新帖） | 平衡 | 实现复杂 |

本项目用的是**推模式**，代码在 `FeedServiceImpl.pushNewPost()`：

```java
// 发帖后调用：查两种粉丝 → 合并去重 → 给每个粉丝的时间线 ZADD
List<Follow> userFans = ...;   // 关注了该作者的人（follow_type=1）
List<Follow> barFans  = ...;   // 关注了该吧的人（follow_type=2）
for (Long fanId : fanIds) {
    stringRedisTemplate.opsForZSet()
            .add("dbd:feed:user:" + fanId, String.valueOf(postId), timestamp);
}
```

> **必答话术**：为什么选推模式？论坛场景"读 Feed"远多于"发帖"，推模式把成本放在低频的写侧，读侧 O(1)。但要主动补一句：**如果出现百万粉丝的大 V，写扩散会撑不住，应该切推拉结合**——主动说出边界，面试官会认为你真懂。

### 3.3 为什么用 ZSet？score 怎么设计？

- **member = postId**（存什么）
- **score = 发帖时间戳（毫秒）**（按什么排序）
- ZSet 天生按 score 排序 + 支持范围查询，`ZREVRANGEBYSCORE` 一次拿到"某时间之前的最新 N 条"

### 3.4 滚动分页：为什么不用 offset？

普通分页 `LIMIT offset, size` 的问题：翻到第 10 页时，**每翻一页都要把前面全部数据扫一遍**，而且翻页期间有新帖插入会导致**重复/错位**（刷抖音时"翻页看到重复内容"就是这个）。

游标分页（本项目 `FeedServiceImpl.feed()`）：

```java
// lastId = 上一页最后一条的 score；-1 实现排他区间（不重复）
double maxScore = lastId == null ? Double.MAX_VALUE : (double) (lastId - 1);
Set<TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
        .reverseRangeByScoreWithScores(key, 0, maxScore, 0, size + 1L);
// 多取一条判断 hasMore；返回本页最后一条的 score 作为下次的 lastId
```

前端 `FeedView.vue` 用"加载更多"按钮携带 lastId 逐页拉取。

### 3.5 懒构建兜底（本项目亮点）

问题：推模式只对新发的帖生效——用户关注之前的**历史帖子**不在时间线里；Redis 重启后时间线也丢了。

对策：`feed()` 发现时间线 key 不存在时，从 follow 表拉出关注对象，把他们的**最近 200 条帖子**一次性 ZADD 进时间线（`rebuildFeed()`）。GEO、排行榜同样有懒构建。

> **面试包装**：这叫"缓存重建的兜底策略"——不用定时任务全量刷，而是**首次访问时惰性重建**，既保证 Redis 重启自愈，又不浪费资源刷没人看的数据。

---

## 4. 分布式锁与防重复提交（SETNX）

### 4.1 为什么 SETNX 能当锁？

`SET key value NX EX seconds`：**原子地**完成"仅在 key 不存在时设置，并带过期时间"。并发下只有一个请求能成功，成功的那个"拿到锁"。

本项目两处使用：

| 场景 | Key | TTL | 作用 |
|---|---|---|---|
| 发帖/回帖防重复提交 | `dbd:repeat:post:{userId}` | 3 秒 | 连点/双击拦截，返回 3002"操作太快" |
| 缓存重建互斥锁 | `dbd:post:cache:{id}:lock` | 10 秒 | 防击穿（§2.2） |

```java
// 发帖防重：先校验后上锁（校验失败不占锁）
Boolean locked = stringRedisTemplate.opsForValue()
        .setIfAbsent(RedisKeyConstants.REPEAT_POST + userId, "1", RedisKeyConstants.REPEAT_POST_TTL);
if (Boolean.FALSE.equals(locked)) {
    throw BusinessException.tooFast("操作太快，请稍后再试");
}
```

### 4.2 一个 SETNX 锁的"正确姿势"（面试加分）

1. **必须带 TTL**：拿到锁的线程挂了，没 TTL 就永久死锁
2. **解锁要校验身份**：A 的锁过期了 B 拿到，A 醒来把 B 的锁删了——正确做法是解锁时校验 value 是自己的（Lua：`if get(key)==myValue then del(key) end`）
3. **任务超过锁 TTL**：用 Redisson 的看门狗（WatchDog）自动续期

> 如实话术：本项目两把锁都满足 1（带 TTL）；场景上 3 秒防重锁和 10 秒重建锁都远小于 TTL，暂不需要看门狗；要讲清楚 2、3 是 Redisson 存在的原因——**"我知道更完善的方案，且知道为什么本项目不用"**。

---

## 5. 其他 Redis 数据结构的项目化应用

### 5.1 BitMap 签到（`BarServiceImpl.sign()`）

**是什么**：Redis 的 String 按位操作。一个 key 的每一位（0/1）代表一天是否签到。一个用户一个月的签到 = 31 位 = 4 字节，一年 48 字节。

```java
String key = "dbd:sign:" + userId + ":202608";   // 按月一个 key
int offset = today.getDayOfMonth() - 1;           // 15 号 → 第 15 位
Boolean signed = stringRedisTemplate.opsForValue().getBit(key, offset);  // 判重
stringRedisTemplate.opsForValue().setBit(key, offset, true);             // 签到
```

**连续天数算法**（BITFIELD 一次取回，Java 侧算低位连续 1 的个数）：

```java
// BITFIELD GET u(offset+1) 0 → 取第 0 位到第 offset 位的二进制串
long num = bits.get(0);
long days = 0;
while ((num & 1L) == 1L) { days++; num >>>= 1; }   // 从今天往前数连续 1
```

> 面试要点：为什么用 BitMap 不用 Set/关系表？**空间**——Set 存 31 个日期字符串 vs 4 字节位图，差 3~4 个数量级；签到是典型"海量布尔值"场景。

### 5.2 HyperLogLog 统计 UV（`PostServiceImpl.countView()`）

**是什么**：基于概率的基数统计结构。**每个 key 固定 12KB**，能估算 2^64 个不重复元素，标准误差 0.81%。

```java
// 每个访客（或 guest）加进去，重复的自动去重
stringRedisTemplate.opsForHyperLogLog().add("dbd:post:uv:" + id, visitor);
Long uv = stringRedisTemplate.opsForHyperLogLog().size("dbd:post:uv:" + id);
```

> 面试要点：**UV 和 PV 的区别**——PV 用 `INCR`（每次浏览 +1，`dbd:post:view:{id}`），UV 要去重，用 HyperLogLog。为什么不用 Set 存访客？百万访客的 Set 要几十 MB，HLL 恒 12KB。代价：**不精确**（0.81% 误差），UV 统计对精度不敏感，完美匹配。

### 5.3 ZSet 排行榜（`RankServiceImpl`）

**是什么**：ZSet = 有序集合，score 排序。热帖榜、热吧榜、热搜词三个场景：

| 榜单 | Key | score | 更新方式 |
|---|---|---|---|
| 热帖榜 | `dbd:rank:hot:post` | 浏览 + 点赞×2 + 楼层×4 | `@Scheduled` 每 5 分钟重算 |
| 热吧榜 | `dbd:rank:hot:bar` | memberCount | 定时重算 + 关注时实时 ZADD |
| 热搜词 | `dbd:search:hot` | 搜索次数 | 每次搜索 `ZINCRBY` +1 |

```java
// 热帖榜重算：从 DB 全量扫帖，Redis 实时取浏览/点赞数，算热度分
double score = views + likes * 2 + comments * 4;
stringRedisTemplate.opsForZSet().add(RANK_HOT_POST, String.valueOf(post.getId()), score);
```

> 面试要点：① 为什么定时重算而不是实时算？榜单纯读场景，实时算每次都聚合全表；定时快照 + 懒构建（首次访问为空时立即构建）平衡新鲜度和成本。② `ZREVRANGEBYSCORE` / `reverseRange` 取 TopN 是 O(log N + M)。

### 5.4 GEO 同城（`NearbyServiceImpl`）

**是什么**：GEO 底层就是 ZSet + **GeoHash**（经纬度交错编码成一个数，地理位置越近 hash 前缀越像），所以"附近的人/帖子"就是 ZSet 的范围查询。

```java
// 发帖带坐标 → GEOADD（PostServiceImpl.create() 里）
stringRedisTemplate.opsForGeo().add("dbd:geo:post",
        new Point(dto.getX(), dto.getY()), String.valueOf(post.getId()));

// 查询 → GEORADIUS：圆心 + 半径 + 带距离 + 升序
GeoResults<GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
        GEO_POST,
        new Circle(new Point(x, y), new Distance(radius, Metrics.NEUTRAL)),
        GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending());
```

> **版本意识（面试亮点）**：GEOSEARCH 需要 Redis 6.2+，GEORADIUS 兼容 3.2+。本机 Redis 5.0 实测不支持 GEOSEARCH，所以选 GEORADIUS 并在注释里标注"6.2+ 可升级 GEOSEARCH"——**根据实际环境做兼容选型，是工程能力**。

### 5.5 全局唯一 ID 生成器（`RedisIdWorker`）

**是什么**：论坛帖子/楼层/订单 ID 不依赖数据库自增（分库分表后自增会冲突），用 Redis 生成：

```java
// 结构：41 位秒级时间戳（2022-01-01 起算，可用 69 年）| 32 位自增序列号
long count = stringRedisTemplate.opsForValue().increment("dbd:id:post:" + date); // 按天一个 key
return timestamp << 32 | count;   // 单天单业务 42 亿个
```

> 面试要点：为什么不用雪花算法？雪花依赖机器时钟，回拨会重复；Redis 自增不依赖时钟。为什么按天分 key？序列号每天从 0 起，key 自带日期方便清理和排查。

### 5.6 String 会话（token）

```java
// 登录：UUID 作 token，value 存 userId，30 分钟过期
stringRedisTemplate.opsForValue().set("dbd:login:token:" + token,
        String.valueOf(userId), Duration.ofMinutes(30));
// 拦截器：每次请求命中 token 就续期（滑动过期）
stringRedisTemplate.expire(tokenKey, Duration.ofMinutes(30));
```

> 面试要点：为什么不用 JWT？JWT 无状态但**无法主动踢人/注销**；Redis 会话可随时删除。为什么存 userId 而不是整个用户对象？省空间，用户信息按需查库/缓存。

---

## 6. 工程化

### 6.1 接口文档驱动（SpringDoc + API.md）

- 静态约定 `API.md`：统一响应 `{code, msg, data}`、错误码表（4001~4004 秒杀码）、鉴权规则——**先定契约后写代码**
- 运行期 `Swagger UI`（`/swagger-ui.html`）自动生成，前端按文档写 `src/api/*`，联调零沟通

> 面试包装：体现"文档先行 + 前后端解耦"的协作能力。

### 6.2 Docker 部署（面试必问的 Docker 基础点）

| 知识点 | 本项目体现 |
|---|---|
| 多阶段构建 | 构建用 `maven` 镜像，运行只留 `jre` + jar，最终镜像小 |
| 层缓存 | Dockerfile 先 `COPY pom.xml` 下载依赖、再 `COPY src`——改代码不重下依赖，构建快 |
| Compose 编排 | 4 个服务声明式编排，`depends_on: condition: service_healthy` 等 MySQL 就绪再起后端 |
| 健康检查 | mysql `mysqladmin ping` / redis `redis-cli ping` |
| 自动初始化 | `init.sql` 挂载到 `/docker-entrypoint-initdb.d/`，首次启动自动建库灌数据 |
| 配置外置 | `application.yml` 全参数 `${MYSQL_HOST:localhost}` 环境变量注入，同一份配置本地/Docker 通用 |

### 6.3 nginx 反代 + SPA 路由

`dbd-web/nginx.conf` 三个职责：静态托管 + `/api` 反代后端 + `try_files ... /index.html`（Vue history 路由刷新不 404）。

---

## 7. 面试高频追问清单（快问快答）

| # | 问题 | 一句话答案 |
|---|---|---|
| 1 | Redis 为什么快？ | 内存存储 + 单线程避免上下文切换/锁竞争 + IO 多路复用 + 高效数据结构 |
| 2 | Redis 单线程怎么还能扛高并发？ | 瓶颈在网络 IO 不在 CPU，单线程省掉锁和切换；6.0 后 IO 多线程 |
| 3 | 缓存穿透/击穿/雪崩的区别？ | 穿透=查不存在的；击穿=热点 key 过期；雪崩=大批 key 同时过期 |
| 4 | 缓存和 DB 一致性怎么保证？ | 先更库后删缓存为基准，延迟双删兜底，MQ/binlog 保证最终一致 |
| 5 | 秒杀为什么用 Lua？ | 多步操作合并成一次原子执行，且支持条件分支（事务做不到） |
| 6 | 一人一单怎么保证？ | Redis SETNX 标记 + DB 唯一索引双保险 |
| 7 | 秒杀异步落库失败怎么办？ | 补偿：库存 INCR + 删标记；生产加 MQ 重试 |
| 8 | Feed 推模式遇到大 V 怎么办？ | 推拉结合：普通用户推、大 V 拉 |
| 9 | 滚动分页为什么不用 offset？ | 深翻页扫全表 + 插入导致重复/错位；游标分页 O(log N) 且稳定 |
| 10 | SETNX 锁有什么坑？ | 不带 TTL 死锁；解锁不校验身份误删别人的锁 |
| 11 | BitMap 和 Set 统计签到哪个省？ | BitMap：一月 4 字节 vs Set 存 30 个日期 |
| 12 | UV 统计 HyperLogLog 的代价？ | 0.81% 误差换固定 12KB |
| 13 | 排行榜怎么更新？ | 定时重算快照 + 懒构建，热点词实时 ZINCRBY |
| 14 | GEO 底层是什么？ | ZSet + GeoHash 编码 |
| 15 | 为什么不直接用数据库自增 ID？ | 分库分表会冲突；Redis 时间戳+序列全局唯一且不依赖时钟 |
| 16 | token 会话 vs JWT？ | Redis 可主动踢人/续期；JWT 无状态但难注销 |
| 17 | Redis 挂了业务怎么办？ | 缓存类有懒构建兜底自愈；秒杀类 fail-fast；核心数据在 MySQL 不丢 |
| 18 | Docker 多阶段构建的好处？ | 构建依赖不进最终镜像，体积小、攻击面小 |
| 19 | 为什么 nginx 放前端？ | 静态资源 + API 反代 + SPA fallback，一个入口 |
| 20 | 这项目你自己最满意的点？ | 任选：Lua 秒杀全链路 / 懒构建兜底 / 20 并发实测数据 |

---

## 8. 进阶：压测补数字（让简历更有杀伤力）

简历里写"20 并发实测"是功能正确性；再补一组**性能数字**含金量翻倍：

1. 装 JMeter（或 wrk），起后端：`cd dbd-server && mvn spring-boot:run`
2. 登录拿 token → 构造 `POST /api/activity/1002/grab` 请求，线程组 200 并发 × 循环 1 次
3. 观测：**200 并发下 300 库存恰好扣 200、0 超卖、响应时间分布**（预期 QPS 数百~数千，取决于机器）
4. 对照实验：详情接口"缓存 miss vs 命中"两次压测的 RT 对比（预期差 10~50 倍）

拿到数字后简历秒杀条目可升级为：*"JMeter 200 并发压测：0 超卖，平均响应 Xms"*。

---

## 9. 学习路线建议

1. **第一遍**：对着代码通读本文档（每个知识点都有文件路径和代码行），运行项目点一点功能
2. **第二遍**：遮住答案，用第 7 章 20 题自测，卡壳的回到对应章节
3. **第三遍**：面试前，把第 0 章全景图默画一遍——能画出这张图，说明知识已经成体系
