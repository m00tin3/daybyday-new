-- ============================================================
-- Day-BY-Day (DbD) 数据库初始化脚本（全量：建库建表 + 演示数据）
-- 库：dbd    字符集：utf8mb4
-- 约定：主键 BIGINT 由后端 Redis 全局 ID 生成器产生（不依赖 AUTO_INCREMENT）
--       sign / uv / Feed / GEO / 秒杀库存 数据由 Redis 承载，不建表
-- 执行：mysql -u root -p < init.sql
-- Docker：mysql 容器启动时自动执行（docker-entrypoint-initdb.d 挂载）
-- ============================================================

-- 强制会话字符集为 utf8mb4。
-- 必须放在所有含中文的语句之前：MySQL 容器内客户端的 character_set_client
-- 默认为 latin1，若不设置，本文件中的 UTF-8 中文会被按 Latin-1 解读后再转存，
-- 造成"二次编码"乱码（例如 猫 被存成 C3A7C592C2AB 而非 E78CAB）。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS dbd DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dbd;

-- ==================== 用户 ====================
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT       NOT NULL COMMENT '用户ID（全局ID生成器）',
  `phone`       VARCHAR(20)  NOT NULL COMMENT '登录账号（手机号或管理员标识）',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密，验证码登录注册时存随机串）',
  `nickname`    VARCHAR(32)  NOT NULL COMMENT '昵称',
  `icon`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `sign_text`   VARCHAR(128) DEFAULT NULL COMMENT '个性签名',
  `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色 0普通用户 1管理员',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户';

-- ==================== 吧 ====================
CREATE TABLE IF NOT EXISTS `bar` (
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
CREATE TABLE IF NOT EXISTS `post` (
  `id`           BIGINT       NOT NULL COMMENT '帖子ID',
  `bar_id`       BIGINT       NOT NULL COMMENT '所属吧ID',
  `user_id`      BIGINT       NOT NULL COMMENT '发帖人ID',
  `title`        VARCHAR(64)  NOT NULL COMMENT '标题',
  `content`      MEDIUMTEXT   NOT NULL COMMENT '正文',
  `images`       VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表（JSON数组）',
  `city`         VARCHAR(32)  DEFAULT NULL COMMENT '城市（发帖时手动填写，用于按城市浏览）',
  -- 以下两列为 GEO 同城所用；当前因无地图 SDK，经纬度手工输入体验差，该功能已封存。
  -- 字段保留以便将来恢复，届时发帖重新写入即可。
  `longitude`    DECIMAL(10,6) DEFAULT NULL COMMENT '经度（GEO 同城，功能已封存）',
  `latitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '纬度（GEO 同城，功能已封存）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除 2精华 3隐藏（隐藏态前台全链路不可见，仅管理后台可见）',
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
  KEY `idx_city` (`city`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB COMMENT='帖子';

-- ==================== 评论/楼层 ====================
CREATE TABLE IF NOT EXISTS `comment` (
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
CREATE TABLE IF NOT EXISTS `post_like` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子点赞（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 收藏 ====================
CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子收藏（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 关注关系 ====================
CREATE TABLE IF NOT EXISTS `follow` (
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
CREATE TABLE IF NOT EXISTS `activity` (
  `id`          BIGINT      NOT NULL COMMENT '活动ID',
  `bar_id`      BIGINT      DEFAULT NULL COMMENT '关联吧ID（抢楼活动所在吧）',
  `title`       VARCHAR(64) NOT NULL COMMENT '活动标题',
  `type`        TINYINT     NOT NULL COMMENT '类型 1抢楼 2限量徽章',
  `badge_name`  VARCHAR(32) DEFAULT NULL COMMENT '限量徽章称号（type=2 时使用）',
  `stock`       INT         NOT NULL COMMENT '库存（徽章数量/楼层上限）',
  `award_desc`  VARCHAR(255) DEFAULT NULL COMMENT '奖励描述',
  `begin_time`  DATETIME    NOT NULL COMMENT '开始时间',
  `end_time`    DATETIME    NOT NULL COMMENT '结束时间',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0未开始 1进行中 2已结束',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_badge_name` (`badge_name`) COMMENT '一个称号同时只能有一个活动',
  KEY `idx_time` (`begin_time`, `end_time`)
) ENGINE=InnoDB COMMENT='秒杀活动';

-- ==================== 秒杀订单/领取记录 ====================
CREATE TABLE IF NOT EXISTS `activity_order` (
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

-- ============================================================
-- 演示数据（INSERT IGNORE：重复执行安全；ID 为小固定值，与全局 ID 生成器互不冲突）
-- ============================================================

-- 演示用户（验证码登录模式；演示验证码固定 123456，见 application.yml app.sms.mock）
INSERT IGNORE INTO `user` (`id`, `phone`, `password`, `nickname`, `sign_text`) VALUES
  (9001, '13800000001', 'demo', '北京同城用户', '人在北京，周末爱逛胡同'),
  (9002, '13800000002', 'demo', '铲屎官小王', '家有三只猫，日常被猫统治'),
  (9003, '13800000003', 'demo', '游戏老玩家', '全职业强度党，攻略区常驻'),
  (9004, '13800000004', 'demo', '上岸人', '早七晚十一，一战成硕'),
  (9005, '13800000005', 'demo', '干饭魂', '深夜放毒专业户，美食探店'),
  (9006, '13800000006', 'demo', '新手上路', '第一辆车，请多指教');

-- 管理员账号（role=1）：登录后可进入 /admin 管理后台
-- ⚠️ 管理员免验证码登录由 app.admin.free-login 控制（.env 的 ADMIN_FREE_LOGIN）。
--    公开部署时建议改为 false，或把下面账号改成只有你知道的标识，
--    否则任何知道该账号的人都能进入管理后台删除内容。
INSERT IGNORE INTO `user` (`id`, `phone`, `password`, `nickname`, `sign_text`, `role`) VALUES
  (9000, '2485617328', 'admin', '系统管理员', '社区管理员', 1);

-- 基础吧
INSERT IGNORE INTO `bar` (`id`, `name`, `description`, `member_count`, `post_count`) VALUES
  (1, '猫咪吧',      '铲屎官交流乐园', 1280000, 32000),
  (2, '游戏攻略吧',  '全职业强度分析、配装攻略', 960000, 51000),
  (3, '考研上岸吧',  '打卡互助，一战成硕', 730000, 26000),
  (4, '汽车之家吧',  '选车用车，老司机带路', 520000, 18000),
  (5, '干饭魂',      '深夜放毒，美食探店', 410000, 15000);

-- 帖子（2001-2005 带坐标 → 同城演示；2010+ 各吧内容；时间错开保证列表排序层次）
INSERT IGNORE INTO `post`
  (`id`, `bar_id`, `user_id`, `title`, `content`, `status`, `is_top`,
   `like_count`, `favorite_count`, `comment_count`, `view_count`, `uv_count`, `score`,
   `last_comment_time`, `created_at`, `longitude`, `latitude`) VALUES
  -- 北京坐标帖（同城 GEO 演示；comment_count 与下方 comment 种子楼层一致）
  (2001, 5, 9001, '天安门附近有推荐的早餐摊吗', '周末去天安门看升旗，求附近好吃的早餐，豆腐脑油条那种老北京味。', 1, 0, 12, 3, 3, 120, 100, 200.00, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), 116.397128, 39.908723),
  (2002, 5, 9001, '故宫角楼咖啡打卡', '东华门那家角楼咖啡，下午去人不多，桂花拿铁好喝，适合逛完故宫歇脚。', 1, 0, 30, 6, 4, 300, 260, 350.00, DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), 116.397950, 39.918120),
  (2003, 3, 9001, '王府井书店自习攻略', '王府井书店三层靠窗位置安静，考研党可以来，就是周末人多要早到。', 1, 0, 5, 2, 0, 80, 60, 120.00, DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY), 116.411200, 39.909600),
  (2004, 5, 9001, '前门大栅栏逛吃路线', '前门大街一路往南，鲜鱼口小吃街 + 大栅栏老字号，半天逛吃路线分享。', 1, 0, 8, 4, 0, 150, 120, 180.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 116.398000, 39.899000),
  (2005, 1, 9001, '三里屯撸猫咖啡馆推荐', '三里屯附近有家猫咖，布偶猫超级亲人，铲屎官们周末约起来。', 1, 0, 20, 5, 0, 260, 200, 300.00, DATE_SUB(NOW(), INTERVAL 30 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), 116.455000, 39.933000),
  -- 猫咪吧
  (2010, 1, 9002, '我家布偶的迷惑行为大赏', '凌晨三点把我拍醒要吃的，吃完又对我翻白眼。整理了它最近的十个迷惑行为，欢迎评论区补充。', 2, 0, 45, 12, 5, 890, 700, 800.00, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
  (2011, 1, 9002, '求助！猫咪半夜跑酷怎么办', '每天凌晨两点准时开运动会，楼下邻居已经找过我两次了，跪求解决方案！', 1, 0, 18, 3, 0, 430, 320, 400.00, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
  (2012, 1, 9001, '【置顶】领养代替购买，北京领养渠道汇总', '整理了北京地区靠谱的流浪猫领养渠道和注意事项，想养猫的朋友先看看这个帖子。', 1, 1, 66, 21, 0, 1500, 1200, 1500.00, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 10 DAY), 116.410000, 39.920000),
  (2025, 1, 9002, '猫粮测评：进口和国产怎么选', '养猫三年换了八种粮，从成分、适口性、性价比三个维度聊聊我的心得。', 1, 0, 26, 8, 0, 900, 750, 600.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  -- 游戏攻略吧
  (2013, 2, 9003, '【精华】新版本全职业强度排行榜（个人向）', '更新后玩了三天，法师依旧 T0，刺客崛起，战士下水道实锤。楼下附详细配装与数据测试。', 2, 0, 320, 88, 0, 5200, 4300, 4800.00, DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
  (2014, 2, 9003, '副本开荒队招人，今晚八点', '缺一个奶妈一个坦克，要求装等 250+，语音配合，萌新勿扰。', 1, 0, 12, 4, 0, 260, 200, 240.00, DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  (2015, 2, 9006, '萌新入坑三天，求大佬带', '刚入坑三天，主线打完了不知道干什么，装备也不会搭配，有大佬收徒吗？', 1, 0, 8, 15, 0, 380, 300, 350.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
  (2028, 2, 9003, '赛季奖励领取时间表', '整理了本赛季所有奖励的领取时间与截止时间，记得别过期。', 1, 0, 9, 2, 0, 400, 330, 300.00, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
  -- 考研上岸吧
  (2016, 3, 9004, '【置顶】倒计时 150 天打卡帖，互相监督', '每天早 7 晚 11，图书馆一楼靠窗位置。想一起的留个言，我们组个队互相卷。', 1, 1, 143, 210, 0, 6800, 5500, 6000.00, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL),
  (2017, 3, 9004, '【精华】英语真题二刷方法论', '一刷重词汇，二刷重逻辑。分享我的真题二刷复盘模板，附时间安排。', 2, 0, 89, 32, 0, 2100, 1700, 1900.00, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
  (2018, 3, 9006, '二战上岸经验：别踩我踩过的坑', '一战败在心态和院校选择，二战调整后上岸。写下这篇长文，希望对你有用。', 1, 0, 210, 45, 0, 4100, 3600, 3800.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL),
  (2026, 3, 9004, '数学强化阶段网课选择', '武老师还是汤老师？两个都听过，说说优缺点和适合人群。', 1, 0, 15, 8, 0, 700, 580, 650.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL),
  -- 汽车之家吧
  (2019, 4, 9006, '第一辆车怎么选？10 万预算求推荐', '刚工作两年，预算十万出头，主要上下班通勤+周末自驾游，油车电车都行，求老哥们给点建议。', 1, 0, 56, 67, 0, 2300, 1900, 2200.00, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
  (2020, 4, 9006, '提车作业：三个月用车感受', '三个月开了 5000 公里，说说油耗、空间、车机的真实感受，附首保记录。', 1, 0, 34, 18, 0, 1200, 980, 1100.00, DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  (2021, 4, 9003, '【精华】电车还是油车？通勤党实测', '每天通勤 40 公里，油车开了一年换电车，从成本、补能、保值三个角度对比。', 2, 0, 95, 40, 0, 2800, 2300, 2600.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
  (2029, 4, 9006, '自驾京郊路线推荐：雁栖湖', '周末带家人去雁栖湖，分享路线、停车和吃饭的地方，附导航关键点。', 1, 0, 28, 9, 0, 980, 800, 900.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 116.660000, 40.400000),
  -- 干饭魂
  (2022, 5, 9005, '深夜放毒：学校门口的兰州拉面倒闭了', '吃了四年的店，老板说儿子考上公务员接他去大城市享福了。祝老板一切顺利！', 1, 0, 87, 12, 0, 1500, 1200, 1400.00, DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
  (2023, 5, 9005, '【精华】北京胡同美食地图（持续更新）', '按片区整理我吃过且会回头的胡同小店，第一期先放东城，评论区可补充。', 2, 0, 150, 60, 0, 3900, 3200, 3600.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 116.390000, 39.900000),
  (2024, 5, 9001, '前门小吃避雷清单', '前门附近游客多，有些店名不副实。这篇列出我踩过的雷和替代选择。', 1, 0, 42, 20, 0, 1600, 1300, 1500.00, DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), 116.398000, 39.899000),
  (2027, 5, 9005, '周末探店：南锣鼓巷新开的云南菜', '汽锅鸡很正，米线一般。人均 80，周末排队半小时，值得一试。', 1, 0, 33, 11, 0, 1100, 900, 1000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 116.400000, 39.935000);

-- 楼层（帖子 2001/2002/2010 的 comment_count 与楼层数一致；回帖时后端按 max(floor_no) 对齐 Redis 计数器）
INSERT IGNORE INTO `comment` (`id`, `post_id`, `user_id`, `floor_no`, `content`, `parent_id`, `like_count`, `status`, `created_at`) VALUES
  (3001, 2001, 9002, 1, '升旗台西边胡同里有家「护国寺小吃」，豆腐脑一绝，早上去正好。', NULL, 12, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (3002, 2001, 9005, 2, '同求！顺便蹲一个卤煮推荐。', NULL, 5, 1, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
  (3003, 2001, 9006, 3, '前门鲜鱼口往里走，有家老北京早点，油条现炸的。', NULL, 8, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
  (3004, 2002, 9001, 1, '补充：周三会员日第二杯半价。', NULL, 6, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
  (3005, 2002, 9002, 2, '角楼咖啡的窗户位置拍照绝了，就是得赶早。', NULL, 15, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (3006, 2002, 9003, 3, '逛完故宫从东华门出来正好，路线合理。', NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
  (3007, 2002, 9005, 4, '他家桂花拿铁确实可以，甜度刚好。', NULL, 4, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
  (3008, 2010, 9001, 1, '哈哈哈哈同款，我家猫也会半夜拍我脸。', NULL, 22, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (3009, 2010, 9004, 2, '猫：这个家我说了算。', NULL, 18, 1, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
  (3010, 2010, 9005, 3, '建议白天多陪玩消耗精力，晚上能消停点。', NULL, 9, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
  (3011, 2010, 9006, 4, '我家布偶是天使，从不闹腾，哈哈。', NULL, 7, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (3012, 2010, 9002, 5, '今天又是被猫统治的一天。', NULL, 5, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 点赞（Redis Set 为主，本表对账兜底；与页面计数无需严格一致）
INSERT IGNORE INTO `post_like` (`id`, `post_id`, `user_id`, `created_at`) VALUES
  (5001, 2001, 9002, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (5002, 2001, 9005, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
  (5003, 2010, 9001, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (5004, 2010, 9002, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (5005, 2013, 9003, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (5006, 2016, 9004, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (5007, 2019, 9006, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (5008, 2022, 9001, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (5009, 2022, 9005, DATE_SUB(NOW(), INTERVAL 23 HOUR));

-- 收藏（Redis Set 为主，本表对账兜底）
INSERT IGNORE INTO `post_favorite` (`id`, `post_id`, `user_id`, `created_at`) VALUES
  (6001, 2010, 9002, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6002, 2012, 9002, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (6003, 2016, 9004, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (6004, 2018, 9004, DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (6005, 2019, 9006, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (6006, 2029, 9006, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (6007, 2022, 9001, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6008, 2023, 9001, DATE_SUB(NOW(), INTERVAL 5 DAY));

-- 关注关系（type 1 关注用户 / 2 关注吧；演示账号 9001 登录后 Feed 即有内容）
INSERT IGNORE INTO `follow` (`id`, `user_id`, `follow_user_id`, `follow_bar_id`, `follow_type`, `created_at`) VALUES
  (4001, 9001, NULL, 1, 2, DATE_SUB(NOW(), INTERVAL 30 DAY)),
  (4002, 9001, NULL, 5, 2, DATE_SUB(NOW(), INTERVAL 25 DAY)),
  (4003, 9001, 9002, NULL, 1, DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (4004, 9001, 9005, NULL, 1, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (4005, 9002, NULL, 1, 2, DATE_SUB(NOW(), INTERVAL 28 DAY)),
  (4006, 9002, 9001, NULL, 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (4007, 9003, NULL, 2, 2, DATE_SUB(NOW(), INTERVAL 26 DAY)),
  (4008, 9003, 9006, NULL, 1, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (4009, 9004, NULL, 3, 2, DATE_SUB(NOW(), INTERVAL 40 DAY)),
  (4010, 9005, NULL, 5, 2, DATE_SUB(NOW(), INTERVAL 22 DAY)),
  (4011, 9005, 9001, NULL, 1, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (4012, 9006, NULL, 4, 2, DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (4013, 9006, 9003, NULL, 1, DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 限量徽章抢夺活动（type=2；时间窗口相对初始化时刻，库存按稀缺度递进）
-- bar_id 为 NULL：徽章是平台级荣誉，不挂在某个吧下。
INSERT IGNORE INTO `activity` (`id`, `bar_id`, `title`, `type`, `badge_name`, `stock`, `award_desc`, `begin_time`, `end_time`, `status`) VALUES
  (1003, NULL, '限量徽章：苦来兮苦宗主', 2, '苦来兮苦宗主',  1, '全站唯一，仅此一枚',  DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1004, NULL, '限量徽章：凤川祥',       2, '凤川祥',        3, '限量 3 枚，先到先得',  DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1005, NULL, '限量徽章：苏幽离',       2, '苏幽离',        5, '限量 5 枚，先到先得',  DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1006, NULL, '限量徽章：千早樱',       2, '千早樱',       10, '限量 10 枚，先到先得', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);

-- ============================================================
-- 校验（可选执行）
-- SHOW TABLES;
-- SELECT COUNT(*) AS post_count FROM post;
-- ============================================================
