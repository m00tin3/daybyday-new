# Day-BY-Day 简历经历写法（可直接复制）

> 配套：`INTERVIEW_GUIDE.md`（每条经历对应的知识点详解与面试追问）
> 使用说明：按投递岗位选版本，时间按真实开发时间填写；所有技术点与数字均出自项目真实实现与实测，面试可现场演示验证。

---

## 一、主版本（投 Java 后端 / 全栈，推荐）

> **盖楼吧 Day-BY-Day · 贴吧风格社区论坛**（个人项目，独立完成前后端）　*20XX.XX - 20XX.XX*
> 技术栈：Spring Boot 3 / MyBatis-Plus / MySQL 8 / Redis / Vue3 + Element Plus / Docker Compose / Nginx
>
> - **秒杀系统（抢楼/限量徽章）**：编写 Lua 原子脚本，将「库存判断 + 预扣 + SETNX 一人一单」合并为单次 Redis 往返执行，配合 DB 唯一索引双保险与 `@Async` 异步落库失败补偿（回滚库存 + 释放标记）；20 并发实测恰好 1 单成功、19 次拦截、0 超卖
> - **缓存体系**：帖子详情落地缓存三件套——空值缓存防穿透、SETNX 互斥锁 + Double Check 重建防击穿、随机 TTL 防雪崩；列表缓存写后失效，并梳理「延迟双删 / binlog 订阅」一致性升级路径
> - **关注 Feed 流**：基于 ZSet 时间线实现推模式写扩散（发帖推送全部关注者），lastId 游标滚动分页替代深翻页 offset，空时间线从关注关系懒构建兜底，Redis 重启自愈
> - **Redis 多维应用**：BitMap 签到（BITFIELD 连续天数）、HyperLogLog UV 去重、ZSet 热帖/热吧榜定时重算 + 热搜词实时累加、GEO 同城附近帖子、Redis 自增全局 ID（时间戳 + 序列）、token 会话滑动续期
> - **工程化部署**：SpringDoc 接口文档驱动前后端联调；Docker Compose 编排 MySQL/Redis/后端/Nginx 四容器，healthcheck 依赖编排 + 首启自动建库灌入演示数据，一条命令上线，可现场演示

---

## 二、精简版（3 条，简历空间紧张时用）

> **盖楼吧 Day-BY-Day · 贴吧风格社区论坛**（个人项目）　*20XX.XX - 20XX.XX*
> 技术栈：Spring Boot 3 / MyBatis-Plus / Redis / Vue3 / Docker
>
> - 独立开发贴吧风格社区论坛（12 页面 + 30 接口），系统实践缓存三件套、Lua 秒杀、ZSet Feed、BitMap、HyperLogLog、GEO 等 10+ Redis 实战技术点
> - 秒杀模块：Lua 原子脚本单次往返完成库存预扣 + 一人一单，异步落库失败自动补偿；20 并发实测 0 超卖、重复请求全部拦截
> - Docker Compose 一键部署（MySQL/Redis/后端/Nginx 四容器 + 自动初始化），已上线可演示

---

## 三、前端岗变体（投前端时用）

> **盖楼吧 Day-BY-Day · 贴吧风格社区论坛**（个人项目，独立完成前后端）　*20XX.XX - 20XX.XX*
> 技术栈：Vue3 / Vite / Element Plus / Pinia / Vue Router / Axios / Nginx / Docker
>
> - 独立完成 12 个页面与完整交互：帖子列表/详情盖楼、验证码登录、吧主页签到、个人中心、搜索热搜、排行榜、关注 Feed 流滚动分页、同城附近帖子、抢楼秒杀活动
> - 工程能力：Axios 封装统一响应解包 + 401 拦截跳登录、Pinia 用户态持久化、路由登录守卫、PostCard 等组件复用、Element Plus 按需引入
> - 交互细节：Feed 流 lastId 游标"加载更多"、同城/发帖页接入浏览器 Geolocation 定位、活动页库存实时刷新
> - 与自研后端（Spring Boot + Redis 秒杀/缓存/GEO）接口文档驱动联调；Nginx 静态托管 + /api 反代 + SPA 路由 fallback，Docker 一键部署上线

---

## 四、校招版（强调全链路 + 学习能力，可再压缩）

> **盖楼吧 Day-BY-Day · 贴吧风格社区论坛**（独立全栈开发）　*20XX.XX - 20XX.XX*
> 技术栈：Vue3 / Spring Boot 3 / MySQL 8 / Redis / Docker
>
> - 从需求设计、接口文档、数据库建模到前后端实现独立完成全链路，产出 12 页面 + 30 接口的完整社区产品，Docker 一键部署可在线访问
> - 核心亮点秒杀系统：Lua 原子脚本实现库存预扣与一人一单，异步落库失败自动补偿，20 并发实测 0 超卖
> - 系统应用 Redis：缓存三件套、ZSet 时间线 Feed、BitMap 签到、HyperLogLog UV、GEO 同城、全局 ID 生成
> - 输出项目文档四件套：README / API 接口约定 / 面试知识点手册 / 部署指南

---

## 五、使用提示

1. **时间**：按真实开发周期填写（不虚构）；学生可写"课程设计/个人项目"
2. **数字可信度**：正文出现的"20 并发实测"来自项目真实验证记录（阶段三验收做过 20 并发同用户抢测），面试若被追问可复现；想更强可按 `INTERVIEW_GUIDE.md` 第 8 章用 JMeter 补一组压测数字（如 200 并发 QPS）
3. **每条经历 → 对应知识点**（被追问时去 `INTERVIEW_GUIDE.md` 找答案）：

| 简历条目 | 手册章节 |
|---|---|
| Lua 秒杀 / 一人一单 / 异步落库补偿 | 第 1 章 |
| 缓存三件套 / 一致性 | 第 2 章 |
| Feed 推模式 / 滚动分页 / 懒构建 | 第 3 章 |
| BitMap / HLL / ZSet / GEO / 全局 ID / token | 第 5 章 |
| Docker 多阶段 / 编排 / nginx | 第 6 章 |
| 面试快问快答 | 第 7 章 |

4. **一句话总结版本**（自我介绍/项目一句话带过时用）：
> 「一个贴吧风格的社区论坛，核心是完整落地了 Redis 实战技术点——Lua 秒杀、缓存三件套、ZSet 关注流、GEO 同城，前后端独立完成并 Docker 上线，可以现场演示。」
