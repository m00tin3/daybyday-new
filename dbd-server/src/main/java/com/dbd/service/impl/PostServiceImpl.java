package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.dto.CommentDTO;
import com.dbd.dto.NoticeCreateDTO;
import com.dbd.dto.PostDTO;
import com.dbd.entity.Bar;
import com.dbd.entity.Comment;
import com.dbd.entity.Notification;
import com.dbd.entity.Post;
import com.dbd.entity.PostFavorite;
import com.dbd.entity.PostLike;
import com.dbd.entity.User;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.CommentMapper;
import com.dbd.mapper.PostFavoriteMapper;
import com.dbd.mapper.PostLikeMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.mapper.UserMapper;
import com.dbd.service.BadgeService;
import com.dbd.service.FeedService;
import com.dbd.service.NotificationService;
import com.dbd.service.PostCountService;
import com.dbd.service.PostService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.CityStatVO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostRow;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 帖子服务实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 2/3/4）：
 * <ul>
 *   <li>详情缓存三件套：空值缓存（防穿透）+ 互斥锁重建（防击穿）+ 随机 TTL（防雪崩）</li>
 *   <li>首页列表缓存 + 发帖后删除（保持数据一致性）</li>
 *   <li>点赞/收藏：Set 判重与计数，DB 同步落库兜底（唯一索引防重）</li>
 *   <li>楼层号：Redis INCR 全局唯一；防重复提交：SETNX 3 秒</li>
 *   <li>浏览/UV：INCR 计数 + HyperLogLog（详情命中缓存时实时覆盖返回）</li>
 * </ul>
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final UserMapper userMapper;
    private final BarMapper barMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final FeedService feedService;
    /** 作者徽章批量填充（帖子列表/详情/楼层），避免每个作者一次查询 */
    private final BadgeService badgeService;
    /** 点赞/收藏计数回填（以 Redis 为准，DB 那两列从来没被写过） */
    private final PostCountService postCountService;
    /** 回复/点赞提醒（与 PostServiceImpl 无循环依赖：它只依赖 NotificationMapper + RedisIdWorker） */
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每个顶层楼层内联展示几条第 1 页子回复。
     * <p>超过这个数的楼层，前端在该层内做分页（仿贴吧的层内翻页），
     * 再来这一页之外的数据走 {@link #floorReplies}。</p>
     */
    private static final int REPLY_PAGE_SIZE = 10;

    /** 详情缓存基础 TTL：10 分钟 + 0~5 分钟随机偏移（防雪崩） */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final long CACHE_TTL_JITTER = 5 * 60;
    /** 空值缓存 TTL：5 分钟（防穿透，DB 没有的数据短暂缓存"空"） */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);
    /** 重建缓存互斥锁 TTL：10 秒 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    /** 首页列表缓存 TTL：60 秒 */
    private static final Duration LIST_TTL = Duration.ofSeconds(60);

    public PostServiceImpl(PostMapper postMapper, CommentMapper commentMapper,
                           PostLikeMapper postLikeMapper, PostFavoriteMapper postFavoriteMapper,
                           UserMapper userMapper, BarMapper barMapper,
                           StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker,
                           FeedService feedService, BadgeService badgeService,
                           PostCountService postCountService, NotificationService notificationService) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.userMapper = userMapper;
        this.barMapper = barMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.feedService = feedService;
        this.badgeService = badgeService;
        this.postCountService = postCountService;
        this.notificationService = notificationService;
    }

    /* ==================== 列表 ==================== */

    @Override
    public PageResult<PostVO> page(Long barId, Long userId, String city, String keyword, Integer page, Integer size) {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? 10 : Math.min(size, 50);
        // 城市参数：去掉首尾空白，空串按"不筛选"处理，避免 ?city= 命中空城市
        String cityFilter = city == null || city.isBlank() ? null : city.trim();

        // 首页（无任何筛选条件）走列表缓存，降低 DB 压力；
        // 一旦带上城市等条件就直查，避免为每种条件组合各缓存一份
        if (barId == null && userId == null && cityFilter == null && (keyword == null || keyword.isBlank())) {
            String cacheKey = RedisKeyConstants.POST_LIST_HOME + page;
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                PageResult<PostVO> result = parsePage(cached, page, size);
                postCountService.fill(result.getList());
                return result;
            }
            PageResult<PostVO> result = queryPage(null, null, null, null, page, size);
            // 先写缓存再回填计数：缓存里存的应该是 DB 原值（点赞/收藏恒 0），
            // 每请求回填才是实时值 —— 否则刚点的赞会被缓存冻住最长 60 秒（LIST_TTL）
            stringRedisTemplate.opsForValue().set(cacheKey, serialize(result), LIST_TTL);
            postCountService.fill(result.getList());
            return result;
        }
        PageResult<PostVO> result = queryPage(barId, userId, cityFilter, keyword, page, size);
        postCountService.fill(result.getList());
        return result;
    }

    private PageResult<PostVO> queryPage(Long barId, Long userId, String city, String keyword,
                                         Integer page, Integer size) {
        IPage<PostRow> rows = postMapper.selectPostPage(new Page<>(page, size), barId, userId, city, keyword);
        List<PostVO> list = rows.getRecords().stream().map(PostVO::fromRow).toList();
        // 一次批量取全页作者的徽章（内部按用户缓存），列表页不能逐个作者查库
        badgeService.fillPostAuthors(list);
        return PageResult.of(list, rows.getTotal(), page, size);
    }

    /* ==================== 按城市浏览 ==================== */

    @Override
    public List<CityStatVO> cities() {
        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstants.POST_CITIES);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, CityStatVO.class));
            } catch (JsonProcessingException e) {
                log.warn("城市列表缓存反序列化失败，回源查库", e);
                stringRedisTemplate.delete(RedisKeyConstants.POST_CITIES);
            }
        }
        List<CityStatVO> list = postMapper.selectCityStats();
        try {
            stringRedisTemplate.opsForValue()
                    .set(RedisKeyConstants.POST_CITIES, objectMapper.writeValueAsString(list),
                            RedisKeyConstants.POST_CITIES_TTL);
        } catch (JsonProcessingException e) {
            log.warn("城市列表缓存序列化失败", e);
        }
        return list;
    }

    /* ==================== 详情（缓存三件套） ==================== */

    @Override
    public PostVO detail(Long id) {
        PostVO vo = readCache(id);
        if (vo != null) {
            fillRequestState(id, vo);
            return vo;
        }
        // 击穿兜底：只允许一个线程重建缓存，其余等待后直查 DB（保证可用性）
        if (tryLock(id)) {
            try {
                vo = readCache(id);
                if (vo == null) {
                    vo = buildDetail(id);
                    writeCache(id, vo);
                }
            } finally {
                unlock(id);
            }
        } else {
            sleep(50);
            vo = buildDetail(id);
        }
        // 统一语义：帖子不可见（不存在 / 已删除 / 已隐藏 / 所属吧已隐藏）一律抛 2002。
        // 否则首次请求会返回 code=1 + data=null，而第二次命中空值缓存才返回 2002，
        // 同一状态下两次请求结果不一致，前端会渲染空白页而不是"帖子不存在"。
        if (vo == null) {
            throw BusinessException.notFound("帖子不存在");
        }
        fillRequestState(id, vo);
        return vo;
    }

    /** 读缓存：null=未缓存；空字符串=DB 无此帖（空值缓存，防穿透） */
    private PostVO readCache(Long id) {
        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstants.POST_CACHE + id);
        if (cached == null) {
            return null;
        }
        if (cached.isEmpty()) {
            throw BusinessException.notFound("帖子不存在");
        }
        try {
            return objectMapper.readValue(cached, PostVO.class);
        } catch (JsonProcessingException e) {
            log.warn("帖子缓存反序列化失败 postId={}", id, e);
            stringRedisTemplate.delete(RedisKeyConstants.POST_CACHE + id);
            return null;
        }
    }

    /** 写缓存：无帖子缓存空串（5 分钟）；有帖子缓存随机 TTL（10+0~5 分钟，防雪崩） */
    private void writeCache(Long id, PostVO vo) {
        String key = RedisKeyConstants.POST_CACHE + id;
        if (vo == null) {
            stringRedisTemplate.opsForValue().set(key, "", EMPTY_TTL);
            return;
        }
        long ttl = CACHE_TTL.toSeconds() + ThreadLocalRandom.current().nextLong(CACHE_TTL_JITTER);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            log.warn("帖子缓存序列化失败 postId={}", id, e);
        }
    }

    /** 重建详情：DB 查询帖子 + 作者 + 吧名；帖子不可见（不存在/已删除/已隐藏）返回 null */
    private PostVO buildDetail(Long id) {
        Post post = postMapper.selectById(id);
        // 统一可见性判定：隐藏帖(3)与已删除帖(0)对前台均视为不存在
        if (post == null || !Post.isVisible(post.getStatus())) {
            return null;
        }
        User author = userMapper.selectById(post.getUserId());
        // 公告（bar_id 为 NULL）不属于任何吧：跳过吧校验，barName 传 null。
        // 不能依赖 barMapper.selectById(null) 的行为（结果随版本而变），直接短路。
        if (post.getBarId() == null) {
            return PostVO.from(post, author, null);
        }
        Bar bar = barMapper.selectById(post.getBarId());
        // 吧被隐藏/删除时其下帖子一并不可见（与列表 SQL 的 b.status = 1 保持一致）
        if (bar == null || bar.getStatus() == null || bar.getStatus() != 1) {
            return null;
        }
        return PostVO.from(post, author, bar.getName());
    }

    /** 互斥锁：SETNX 抢占，重建完成后释放 */
    private boolean tryLock(Long id) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.POST_CACHE + id + ":lock", "1", LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(Long id) {
        stringRedisTemplate.delete(RedisKeyConstants.POST_CACHE + id + ":lock");
    }

    /**
     * 请求级状态：当前用户是否点赞/收藏（实时查 Redis），浏览/UV 计数，作者徽章。
     *
     * <p>徽章刻意放在这里而不是 buildDetail 里：buildDetail 的结果会写入
     * 10 分钟详情缓存，把徽章一起缓存进去的话，用户刚抢到徽章也要等缓存过期
     * 才在帖子详情看得到。放在本方法（缓存命中/未命中两条路径都会走）里，
     * 每次请求都按当前的徽章缓存重新覆盖一次，缓存里存的始终是不带徽章的版本。</p>
     */
    private void fillRequestState(Long id, PostVO vo) {
        if (vo == null) {
            return;
        }
        Long userId = UserContext.get();
        if (userId != null) {
            vo.setIsLiked(isMember(RedisKeyConstants.POST_LIKE, id, userId));
            vo.setIsFavorited(isMember(RedisKeyConstants.POST_FAVORITE, id, userId));
        } else {
            vo.setIsLiked(false);
            vo.setIsFavorited(false);
        }
        // 点赞/收藏数同样以 Redis 为准：DB 的 like_count / favorite_count 从来没被写过，
        // 直接读会恒为 0（浏览量之所以正常，是因为下面的 countView 每请求都覆盖了一次）
        postCountService.fill(List.of(vo));
        badgeService.fillAuthor(vo.getAuthor());
        countView(id, vo);
    }

    /** 浏览 +1、UV 去重计数（HyperLogLog），并把最新计数覆盖到返回的 VO 上 */
    private void countView(Long id, PostVO vo) {
        String visitor = UserContext.get() == null ? "guest" : String.valueOf(UserContext.get());
        stringRedisTemplate.opsForHyperLogLog().add(RedisKeyConstants.POST_UV + id, visitor);
        Long views = stringRedisTemplate.opsForValue().increment(RedisKeyConstants.POST_VIEW + id);
        if (views != null) {
            vo.setViewCount(views);
        }
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(RedisKeyConstants.POST_UV + id);
        vo.setUvCount(uv);
    }

    /* ==================== 发帖 ==================== */

    @Override
    public Long create(PostDTO dto) {
        Long userId = UserContext.get();
        if (barMapper.selectById(dto.getBarId()) == null) {
            throw BusinessException.notFound("吧不存在");
        }
        // 防重复提交：SETNX 3 秒内同一用户只能发一帖（双击/连点兜底）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.REPEAT_POST + userId, "1", RedisKeyConstants.REPEAT_POST_TTL);
        if (Boolean.FALSE.equals(locked)) {
            throw BusinessException.tooFast("操作太快，请稍后再试");
        }
        Post post = new Post();
        post.setId(redisIdWorker.nextId("post"));
        post.setBarId(dto.getBarId());
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setImages(serializeImages(dto.getImages()));
        // 城市：发帖时手动填写，用于"按城市浏览"（替代已封存的 GEO 同城）。
        // 空串统一落 NULL，避免出现 city='' 这种既不是有效城市又非空的脏值
        post.setCity(dto.getCity() == null || dto.getCity().isBlank() ? null : dto.getCity().trim());
        post.setStatus(1);
        // 显式写类型：详情/列表都直接读实体，不能依赖 DB 的 DEFAULT 0
        post.setType(Post.TYPE_NORMAL);
        post.setIsTop(0);
        post.setLikeCount(0);
        post.setFavoriteCount(0);
        post.setCommentCount(0);
        post.setViewCount(0);
        post.setUvCount(0);
        // 新帖即最新回复：显式写入创建/最后回复时间（列表按最后回复倒序，同时作为 Feed 时间线 score）
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setLastCommentTime(now);
        // 说明：原先此处是「发帖带经纬度 → GEOADD 写入 dbd:geo:post」。
        // GEO 同城已封存（缺少地图 SDK，手输经纬度体验差且无法校验），故不再写 GEO 索引。
        // 恢复方式：给 PostDTO 加回 x/y 字段并在此重写 GEOADD，其余代码无需改动。
        postMapper.insert(post);
        // 发帖后删首页列表缓存；新帖可能带来新城市，城市列表缓存一并失效
        deleteHomeListCache();
        deleteCitiesCache();
        // Feed 写扩散：新帖推入关注该作者/该吧的粉丝时间线
        feedService.pushNewPost(post.getId(), post.getUserId(), post.getBarId(),
                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return post.getId();
    }

    @Override
    public Long publishNotice(NoticeCreateDTO dto) {
        // 角色校验不在这里：调用方是 AdminService，即 /api/admin/** 路径，
        // 已由 AdminInterceptor 强制 role=1（未登录 401 / 非管理员 403）
        Long userId = UserContext.get();
        Post post = new Post();
        post.setId(redisIdWorker.nextId("post"));
        // 公告不挂任何吧 —— 这个 NULL 就是"全站公告"的语义载体，
        // 列表 SQL 用 LEFT JOIN + (p.bar_id IS NULL OR b.status = 1) 放行它
        post.setBarId(null);
        post.setType(Post.TYPE_ANNOUNCEMENT);
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setImages(null);
        // 不填城市：公告是平台级内容，不应出现在"按城市浏览"里
        post.setCity(null);
        post.setStatus(Post.STATUS_NORMAL);
        // 公告恒置顶，配合列表的 ORDER BY p.type DESC, p.is_top DESC 排在全站最前
        post.setIsTop(1);
        post.setLikeCount(0);
        post.setFavoriteCount(0);
        post.setCommentCount(0);
        post.setViewCount(0);
        post.setUvCount(0);
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setLastCommentTime(now);
        postMapper.insert(post);
        // 发布公告会新增一条置顶帖，首页列表缓存必须失效，否则最长 60 秒内看不到
        deleteHomeListCache();
        deleteCitiesCache();
        // 关注了管理员的人能在关注流里看到公告；公告没有所属吧，barId 传 null
        feedService.pushNewPost(post.getId(), userId, null,
                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        log.info("发布公告 postId={}, title={}", post.getId(), post.getTitle());
        return post.getId();
    }

    /* ==================== 点赞 / 收藏 ==================== */

    @Override
    public Map<String, Object> like(Long postId) {
        Post post = requirePost(postId);
        Long userId = UserContext.get();
        return toggleMember(RedisKeyConstants.POST_LIKE, postId, userId,
                () -> {
                    PostLike row = new PostLike();
                    row.setId(redisIdWorker.nextId("like"));
                    row.setPostId(postId);
                    row.setUserId(userId);
                    postLikeMapper.insert(row);
                    // 只在"0→1 新点赞"这一刻发提醒。取消点赞既不撤回旧通知（通知是历史记录，
                    // 不回滚），也不产生新通知；反复赞/取消由 NotificationService 按
                    // (接收者, 触发者, 类型, 帖子) 去重，不会刷屏。
                    notificationService.notify(post.getUserId(),
                            Notification.TYPE_LIKE_POST, userId, postId, null);
                },
                () -> postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, userId)),
                "isLiked");
    }

    @Override
    public Map<String, Object> favorite(Long postId) {
        requirePost(postId);
        Long userId = UserContext.get();
        return toggleMember(RedisKeyConstants.POST_FAVORITE, postId, userId,
                () -> {
                    PostFavorite row = new PostFavorite();
                    row.setId(redisIdWorker.nextId("favorite"));
                    row.setPostId(postId);
                    row.setUserId(userId);
                    postFavoriteMapper.insert(row);
                },
                () -> postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getPostId, postId).eq(PostFavorite::getUserId, userId)),
                "isFavorited");
    }

    /**
     * 点赞/收藏通用切换：Set 判断 → 增删 + DB 落库兜底，返回 { 状态键: bool, count键: count }。
     */
    private Map<String, Object> toggleMember(String setKey, Long postId, Long userId,
                                             Runnable onInsert, Runnable onDelete, String activeKey) {
        String member = String.valueOf(userId);
        boolean isActive = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey + postId, member));
        if (isActive) {
            stringRedisTemplate.opsForSet().remove(setKey + postId, member);
            onDelete.run();
        } else {
            stringRedisTemplate.opsForSet().add(setKey + postId, member);
            onInsert.run();
        }
        Long count = stringRedisTemplate.opsForSet().size(setKey + postId);
        Map<String, Object> result = new HashMap<>();
        result.put(activeKey, !isActive);
        result.put(activeKey.equals("isLiked") ? "likeCount" : "favoriteCount", count);
        return result;
    }

    /* ==================== 楼层 ==================== */

    @Override
    public PageResult<CommentVO> comments(Long postId, Integer page, Integer size) {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? 10 : Math.min(size, 50);
        requirePost(postId);

        IPage<Comment> rows = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .isNull(Comment::getParentId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getFloorNo));
        List<Comment> records = rows.getRecords();

        // 本页顶层楼层的子回复：一次 IN 查完并按 parentId 分组，而不是逐层查（N+1）。
        // 说明：这里把本页所有子回复都取回来了（用于得到每组的总数），
        // 量级 = 本页楼层数 × 每层回复数。演示规模下没问题；若日后热帖单层回复上千，
        // 再改成"分组 COUNT + 每组 LIMIT 取前 N"两条查询。
        Map<Long, List<Comment>> repliesByParent = records.isEmpty() ? Map.of()
                : commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                                .in(Comment::getParentId, records.stream().map(Comment::getId).toList())
                                .eq(Comment::getStatus, 1)
                                .orderByAsc(Comment::getCreatedAt))
                        .stream().collect(Collectors.groupingBy(Comment::getParentId));

        // 顶层楼层作者 + 子回复作者 + 子回复的"被回复者"一次性批量查，避免逐条回表。
        // 被回复者也要查：前端要显示「回复 @某某」，而目标那条可能不在这几条预览里，
        // 前端无从反查昵称，只能这里顺手带上。
        Set<Long> authorIds = new HashSet<>();
        records.forEach(c -> authorIds.add(c.getUserId()));
        repliesByParent.values().forEach(children -> children.forEach(c -> {
            authorIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                authorIds.add(c.getReplyToUserId());
            }
        }));
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<CommentVO> list = new ArrayList<>(records.size());
        // 徽章要连子回复作者一起批量挂，否则楼中楼里的昵称没有角标
        List<CommentVO> badgeTargets = new ArrayList<>();
        for (Comment c : records) {
            CommentVO vo = CommentVO.from(c, userMap.get(c.getUserId()));
            List<Comment> children = repliesByParent.getOrDefault(c.getId(), List.of());
            // replyCount 是**总数**（前端据此决定要不要在该层内分页）；
            // replies 是**第 1 页**，超过 REPLY_PAGE_SIZE 的部分前端翻页时走 floorReplies 拉
            vo.setReplyCount((long) children.size());
            List<CommentVO> firstPage = children.stream()
                    .limit(REPLY_PAGE_SIZE)
                    .map(child -> {
                        CommentVO childVo = CommentVO.from(child, userMap.get(child.getUserId()));
                        User repliedTo = userMap.get(child.getReplyToUserId());
                        childVo.setReplyToNickname(repliedTo == null ? null : repliedTo.getNickname());
                        return childVo;
                    })
                    .toList();
            vo.setReplies(firstPage);
            list.add(vo);
            badgeTargets.add(vo);
            badgeTargets.addAll(firstPage);
        }
        badgeService.fillCommentAuthors(badgeTargets);
        return PageResult.of(list, rows.getTotal(), page, size);
    }

    /**
     * 某一层楼下的子回复分页（楼中楼翻页用）。
     *
     * <p>为什么单开一个接口而不是让 {@link #comments} 一次全给：一层楼的子回复可能很多，
     * 主列表只该带第 1 页；翻到第 N 页时按 (post_id, parent_id) 索引精确取一页，
     * 每次只传 size 条。</p>
     *
     * @param floorId 顶层楼层ID（传子回复的 id 会 2002 —— 两层结构下只有顶层才有"下一页"）
     */
    @Override
    public PageResult<CommentVO> floorReplies(Long postId, Long floorId, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? REPLY_PAGE_SIZE : Math.min(size, 50);
        requirePost(postId);

        Comment floor = commentMapper.selectById(floorId);
        // 必须是本帖的顶层楼层：否则能拿 A 帖的楼层 id 去 B 帖翻出别人的子回复
        if (floor == null || !postId.equals(floor.getPostId()) || floor.getParentId() != null) {
            throw BusinessException.notFound("楼层不存在");
        }

        IPage<Comment> rows = commentMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getParentId, floorId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreatedAt)
                        .orderByAsc(Comment::getId));
        return PageResult.of(toCommentVOs(rows.getRecords()), rows.getTotal(), p, s);
    }

    /**
     * 把楼层/子回复实体批量转成 VO：作者与被回复者昵称一次查完，徽章一次挂完。
     * <p>{@link #comments} 因为要跨"楼层 + 子回复"合并查用户，没有走这里。</p>
     */
    private List<CommentVO> toCommentVOs(List<Comment> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        records.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<CommentVO> list = new ArrayList<>(records.size());
        for (Comment c : records) {
            CommentVO vo = CommentVO.from(c, userMap.get(c.getUserId()));
            User repliedTo = userMap.get(c.getReplyToUserId());
            vo.setReplyToNickname(repliedTo == null ? null : repliedTo.getNickname());
            list.add(vo);
        }
        badgeService.fillCommentAuthors(list);
        return list;
    }

    @Override
    public Map<String, Object> addComment(Long postId, CommentDTO dto) {
        Long userId = UserContext.get();
        Post postRow = requirePost(postId);

        // 楼中楼父楼层校验：必须存在、必须属于本帖、且自身必须是顶层。
        // 后两条不能省：只判"存在"的话，能拿 A 帖的楼层 id 往 B 帖挂回复，
        // 也能对一条子回复再回复（三层）——而两层结构下渲染层根本表达不了三层。
        Comment parent = null;
        if (dto.getParentId() != null) {
            parent = commentMapper.selectById(dto.getParentId());
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw BusinessException.notFound("所回复的楼层不存在");
            }
            if (parent.getParentId() != null) {
                throw BusinessException.param("只支持两级回复，不能再回复楼中楼");
            }
        }

        // 防重复提交：SETNX 3 秒内同一用户只能回一楼（先校验后上锁，校验失败不占锁）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.REPEAT_COMMENT + userId, "1", RedisKeyConstants.REPEAT_COMMENT_TTL);
        if (Boolean.FALSE.equals(locked)) {
            throw BusinessException.tooFast("操作太快，请稍后再试");
        }

        // 楼层号只分配给顶层楼层；楼中楼固定写 0（哨兵，永不展示）。
        // 若子回复也 INCR，就会吃掉一个楼层号，前台顶层楼层号出现 [1,3] 这种空档。
        int floorNo = 0;
        if (parent == null) {
            // 首次回帖先把 Redis 计数器与 DB 最大楼层对齐（种子数据楼层不撞号）
            String floorKey = RedisKeyConstants.POST_FLOOR + postId;
            Long maxFloor = commentMapper.selectMaxFloor(postId);
            if (maxFloor != null && maxFloor > 0) {
                stringRedisTemplate.opsForValue().setIfAbsent(floorKey, String.valueOf(maxFloor));
            }
            floorNo = stringRedisTemplate.opsForValue().increment(floorKey).intValue();
        }

        // 被回复者：默认是父楼层的作者；若明确点了某条评论（可能是子回复），
        // 就以那条评论的作者为准 —— 否则"回复楼中楼的某人"会把通知发给楼主。
        Long replyToUserId = parent == null ? null : parent.getUserId();
        if (dto.getReplyToCommentId() != null) {
            Comment target = commentMapper.selectById(dto.getReplyToCommentId());
            if (target == null || !postId.equals(target.getPostId())) {
                throw BusinessException.notFound("所回复的评论不存在");
            }
            replyToUserId = target.getUserId();
        }

        Comment comment = new Comment();
        comment.setId(redisIdWorker.nextId("comment"));
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setFloorNo(floorNo);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        comment.setReplyToUserId(replyToUserId);
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);

        // 帖子回复数 +1、最后回复时间更新（DB），并删详情/首页缓存保持新鲜。
        //
        // comment_count 必须用**原子自增**，不能"读出来 +1 再写回"：
        // 两个人同时回帖时会丢更新（都读到 5、都写 6，两条回复只涨 1）。
        // 上面那个 SETNX 防重复锁是**按用户**的，挡不住不同用户并发。
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1")
                .set(Post::getLastCommentTime, LocalDateTime.now()));
        deleteCache(postId);
        deleteHomeListCache();

        // 回复提醒：顶层回复通知帖子作者；楼中楼通知被回复者。
        // "自己回复自己不发"由 notify 内部统一兜住，这里不重复判断。
        if (parent == null) {
            notificationService.notify(postRow.getUserId(),
                    Notification.TYPE_REPLY_POST, userId, postId, comment.getId());
        } else {
            notificationService.notify(replyToUserId,
                    Notification.TYPE_REPLY_COMMENT, userId, postId, comment.getId());
        }

        Map<String, Object> result = new HashMap<>();
        // 楼层 ID 同为 Redis 全局 ID，转字符串避免前端 JS 精度丢失
        result.put("id", String.valueOf(comment.getId()));
        result.put("floorNo", floorNo);
        // 前端据此区分"盖楼成功，你是第 N 楼"与"回复成功"（楼中楼 floorNo 恒为 0）
        result.put("parentId", dto.getParentId() == null ? null : String.valueOf(dto.getParentId()));
        return result;
    }

    /* ==================== 管理端支撑 ==================== */

    @Override
    public void evictPostCache(Long postId) {
        deleteCache(postId);
        // 重建互斥锁一并清理：否则隐藏/删除瞬间若残留锁，下次详情重建会被跳过
        stringRedisTemplate.delete(RedisKeyConstants.POST_CACHE + postId + ":lock");
        deleteHomeListCache();
        // 隐藏/删除会改变各城市的帖子数，城市列表缓存同步失效
        deleteCitiesCache();
    }

    @Override
    public void evictHomeListCache() {
        deleteHomeListCache();
    }

    /** 城市列表缓存失效（帖子新增/隐藏/删除都可能改变城市集合或各城市帖子数） */
    private void deleteCitiesCache() {
        stringRedisTemplate.delete(RedisKeyConstants.POST_CITIES);
    }

    /* ==================== 工具 ==================== */

    /** 校验帖子可见并**把它返回**：调用方（点赞通知、回帖计数）正好要用它的作者/旧计数，省一次查库 */
    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        // 统一可见性判定：隐藏帖(3)对前台等同不存在（不可点赞/收藏/回帖/查楼层）
        if (post == null || !Post.isVisible(post.getStatus())) {
            throw BusinessException.notFound("帖子不存在");
        }
        return post;
    }

    private boolean isMember(String setKey, Long postId, Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey + postId, String.valueOf(userId)));
    }

    private void deleteCache(Long postId) {
        stringRedisTemplate.delete(RedisKeyConstants.POST_CACHE + postId);
    }

    private void deleteHomeListCache() {
        for (int p = 1; p <= 5; p++) {
            stringRedisTemplate.delete(RedisKeyConstants.POST_LIST_HOME + p);
        }
    }

    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", images) + "\"]";
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("序列化失败", e);
            return "[]";
        }
    }

    private PageResult<PostVO> parsePage(String json, Integer page, Integer size) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructParametricType(PageResult.class, PostVO.class));
        } catch (JsonProcessingException e) {
            log.warn("列表缓存反序列化失败", e);
            return PageResult.of(List.of(), 0L, page, size);
        }
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
