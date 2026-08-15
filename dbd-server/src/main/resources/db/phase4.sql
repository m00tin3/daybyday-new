-- ============================================================
-- Day-BY-Day 阶段四增量迁移脚本（已有库执行本文件即可，无需重建）
-- 内容：演示用户/各吧帖子/楼层/点赞/收藏/关注关系（与 init.sql 演示数据一致）
-- 执行：mysql -u root -p dbd < phase4.sql
-- 前置：已执行 init.sql（或 phase3.sql 已完成 post 表坐标字段迁移）
-- ============================================================

USE dbd;

-- 演示用户（验证码登录模式，演示验证码固定 123456）
INSERT IGNORE INTO `user` (`id`, `phone`, `password`, `nickname`, `sign_text`) VALUES
  (9002, '13800000002', 'demo', '铲屎官小王', '家有三只猫，日常被猫统治'),
  (9003, '13800000003', 'demo', '游戏老玩家', '全职业强度党，攻略区常驻'),
  (9004, '13800000004', 'demo', '上岸人', '早七晚十一，一战成硕'),
  (9005, '13800000005', 'demo', '干饭魂', '深夜放毒专业户，美食探店'),
  (9006, '13800000006', 'demo', '新手上路', '第一辆车，请多指教');

-- 各吧演示帖子（2010-2029；时间错开保证列表排序层次）
INSERT IGNORE INTO `post`
  (`id`, `bar_id`, `user_id`, `title`, `content`, `status`, `is_top`,
   `like_count`, `favorite_count`, `comment_count`, `view_count`, `uv_count`, `score`,
   `last_comment_time`, `created_at`, `longitude`, `latitude`) VALUES
  (2010, 1, 9002, '我家布偶的迷惑行为大赏', '凌晨三点把我拍醒要吃的，吃完又对我翻白眼。整理了它最近的十个迷惑行为，欢迎评论区补充。', 2, 0, 45, 12, 5, 890, 700, 800.00, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
  (2011, 1, 9002, '求助！猫咪半夜跑酷怎么办', '每天凌晨两点准时开运动会，楼下邻居已经找过我两次了，跪求解决方案！', 1, 0, 18, 3, 0, 430, 320, 400.00, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
  (2012, 1, 9001, '【置顶】领养代替购买，北京领养渠道汇总', '整理了北京地区靠谱的流浪猫领养渠道和注意事项，想养猫的朋友先看看这个帖子。', 1, 1, 66, 21, 0, 1500, 1200, 1500.00, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 10 DAY), 116.410000, 39.920000),
  (2025, 1, 9002, '猫粮测评：进口和国产怎么选', '养猫三年换了八种粮，从成分、适口性、性价比三个维度聊聊我的心得。', 1, 0, 26, 8, 0, 900, 750, 600.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  (2013, 2, 9003, '【精华】新版本全职业强度排行榜（个人向）', '更新后玩了三天，法师依旧 T0，刺客崛起，战士下水道实锤。楼下附详细配装与数据测试。', 2, 0, 320, 88, 0, 5200, 4300, 4800.00, DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
  (2014, 2, 9003, '副本开荒队招人，今晚八点', '缺一个奶妈一个坦克，要求装等 250+，语音配合，萌新勿扰。', 1, 0, 12, 4, 0, 260, 200, 240.00, DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  (2015, 2, 9006, '萌新入坑三天，求大佬带', '刚入坑三天，主线打完了不知道干什么，装备也不会搭配，有大佬收徒吗？', 1, 0, 8, 15, 0, 380, 300, 350.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
  (2028, 2, 9003, '赛季奖励领取时间表', '整理了本赛季所有奖励的领取时间与截止时间，记得别过期。', 1, 0, 9, 2, 0, 400, 330, 300.00, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
  (2016, 3, 9004, '【置顶】倒计时 150 天打卡帖，互相监督', '每天早 7 晚 11，图书馆一楼靠窗位置。想一起的留个言，我们组个队互相卷。', 1, 1, 143, 210, 0, 6800, 5500, 6000.00, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL),
  (2017, 3, 9004, '【精华】英语真题二刷方法论', '一刷重词汇，二刷重逻辑。分享我的真题二刷复盘模板，附时间安排。', 2, 0, 89, 32, 0, 2100, 1700, 1900.00, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
  (2018, 3, 9006, '二战上岸经验：别踩我踩过的坑', '一战败在心态和院校选择，二战调整后上岸。写下这篇长文，希望对你有用。', 1, 0, 210, 45, 0, 4100, 3600, 3800.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL),
  (2026, 3, 9004, '数学强化阶段网课选择', '武老师还是汤老师？两个都听过，说说优缺点和适合人群。', 1, 0, 15, 8, 0, 700, 580, 650.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL),
  (2019, 4, 9006, '第一辆车怎么选？10 万预算求推荐', '刚工作两年，预算十万出头，主要上下班通勤+周末自驾游，油车电车都行，求老哥们给点建议。', 1, 0, 56, 67, 0, 2300, 1900, 2200.00, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
  (2020, 4, 9006, '提车作业：三个月用车感受', '三个月开了 5000 公里，说说油耗、空间、车机的真实感受，附首保记录。', 1, 0, 34, 18, 0, 1200, 980, 1100.00, DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
  (2021, 4, 9003, '【精华】电车还是油车？通勤党实测', '每天通勤 40 公里，油车开了一年换电车，从成本、补能、保值三个角度对比。', 2, 0, 95, 40, 0, 2800, 2300, 2600.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
  (2029, 4, 9006, '自驾京郊路线推荐：雁栖湖', '周末带家人去雁栖湖，分享路线、停车和吃饭的地方，附导航关键点。', 1, 0, 28, 9, 0, 980, 800, 900.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 116.660000, 40.400000),
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

-- 修正 2001/2002 的楼层计数与种子楼层一致（init.sql 已一致；仅修正早期 phase3 种子的旧值）
UPDATE `post` SET `comment_count` = 3 WHERE `id` = 2001;
UPDATE `post` SET `comment_count` = 4 WHERE `id` = 2002;

-- 点赞（Redis Set 为主，本表对账兜底）
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

-- 说明：
-- 首页 / 各吧 / 排行 / 同城 / Feed / 楼层 均有演示数据；
-- 热搜词（ZSet）与签到（BitMap）由 Redis 承载，可现场操作产生（搜索 / 签到）。
