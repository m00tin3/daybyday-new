package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.dto.ActivityCreateDTO;
import com.dbd.dto.BarCreateDTO;
import com.dbd.entity.Activity;
import com.dbd.entity.ActivityOrder;
import com.dbd.entity.Bar;
import com.dbd.entity.Comment;
import com.dbd.entity.Follow;
import com.dbd.entity.Post;
import com.dbd.entity.PostFavorite;
import com.dbd.entity.PostLike;
import com.dbd.mapper.ActivityMapper;
import com.dbd.mapper.ActivityOrderMapper;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.CommentMapper;
import com.dbd.mapper.FollowMapper;
import com.dbd.mapper.PostFavoriteMapper;
import com.dbd.mapper.PostLikeMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.service.AdminService;
import com.dbd.service.ActivityService;
import com.dbd.service.BadgeService;
import com.dbd.service.BarService;
import com.dbd.service.PostService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.ActivityVO;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostRow;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理服务实现。
 *
 * <p><b>核心关注点</b>：隐藏/删除后必须同时处理 <i>DB 状态</i> 与 <i>Redis 残留</i>，
 * 否则因缓存（详情 10 分钟、列表 60 秒、吧信息 5 分钟）与各类 ZSet/Set 索引，
 * 前台在 TTL 内仍能看到已隐藏的内容。</p>
 *
 * <p><b>可见性收口</b>：前台可见性统一由 {@link Post#isVisible(Integer)}（帖子）
 * 与 {@code bar.status = 1}（吧）判定。列表走 SQL，详情/Feed/排行/收藏走内存过滤。</p>
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    /** 首页列表缓存分页上限，与 PostServiceImpl.deleteHomeListCache 保持一致 */
    private static final int HOME_LIST_CACHE_PAGES = 5;

    /** 活动类型：限量徽章（管理端只发布这一种；抢楼为历史类型，保留兼容） */
    private static final int TYPE_BADGE = 2;

    /** 活动时间入参格式，与前端 el-date-picker 的 value-format 保持一致 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PostMapper postMapper;
    private final BarMapper barMapper;
    private final CommentMapper commentMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    /** 删吧时清理指向该吧的关注关系，避免留下孤儿数据 */
    private final FollowMapper followMapper;
    /** 删吧时清理关联活动及其秒杀订单 */
    private final ActivityMapper activityMapper;
    private final ActivityOrderMapper activityOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final PostService postService;
    private final BarService barService;
    /** 活动 VO 组装复用秒杀服务的口径（实时库存/已抢数量/是否已抢），避免前后台展示不一致 */
    private final ActivityService activityService;
    /** 删除活动后要让领取人的徽章缓存失效 */
    private final BadgeService badgeService;

    public AdminServiceImpl(PostMapper postMapper, BarMapper barMapper, CommentMapper commentMapper,
                            PostLikeMapper postLikeMapper, PostFavoriteMapper postFavoriteMapper,
                            FollowMapper followMapper, ActivityMapper activityMapper,
                            ActivityOrderMapper activityOrderMapper,
                            StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker,
                            PostService postService, BarService barService,
                            ActivityService activityService, BadgeService badgeService) {
        this.postMapper = postMapper;
        this.barMapper = barMapper;
        this.commentMapper = commentMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.followMapper = followMapper;
        this.activityMapper = activityMapper;
        this.activityOrderMapper = activityOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.postService = postService;
        this.barService = barService;
        this.activityService = activityService;
        this.badgeService = badgeService;
    }

    /* ==================== 帖子管理 ==================== */

    @Override
    public PageResult<PostVO> postList(String keyword, Integer status, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        // 管理端查询不限制 status，可看到隐藏(3)与精华(2)
        IPage<PostRow> rows = postMapper.selectAdminPostPage(new Page<>(page, size), keyword, status);
        List<PostVO> list = rows.getRecords().stream().map(PostVO::fromRow).toList();
        // 管理端列表同样显示作者徽章，口径与前台一致
        badgeService.fillPostAuthors(list);
        return PageResult.of(list, rows.getTotal(), page, size);
    }

    @Override
    public void hidePost(Long postId) {
        Post post = requirePost(postId);
        if (post.getStatus() != null && post.getStatus() == Post.STATUS_HIDDEN) {
            throw BusinessException.param("该帖子已处于隐藏状态");
        }
        updatePostStatus(postId, Post.STATUS_HIDDEN);
        postService.evictPostCache(postId);
        log.info("管理员隐藏帖子 postId={}, 原状态={}", postId, post.getStatus());
    }

    @Override
    public void restorePost(Long postId) {
        Post post = requirePost(postId);
        if (post.getStatus() != null && post.getStatus() == Post.STATUS_NORMAL) {
            throw BusinessException.param("该帖子已是正常状态");
        }
        updatePostStatus(postId, Post.STATUS_NORMAL);
        postService.evictPostCache(postId);
        log.info("管理员恢复帖子 postId={}, 原状态={}", postId, post.getStatus());
    }

    @Override
    public void deletePost(Long postId) {
        Post post = requirePost(postId);
        // 1) 先清关联数据（表间无外键约束，不清理会留下指向已删帖子的脏数据）
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId));
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
        // 2) 物理删除主记录
        postMapper.deleteById(postId);
        // 3) 清 Redis 残留（缓存 + 计数 + 各类索引）
        purgePostRedis(postId);
        log.warn("管理员物理删除帖子 postId={}, title={}", postId, post.getTitle());
    }

    /* ==================== 吧管理 ==================== */

    @Override
    public PageResult<BarVO> barList(String keyword, Integer status, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        LambdaQueryWrapper<Bar> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Bar::getName, keyword.trim());
        }
        if (status != null) {
            qw.eq(Bar::getStatus, status);
        }
        qw.orderByDesc(Bar::getCreatedAt);
        IPage<Bar> rows = barMapper.selectPage(new Page<>(page, size), qw);
        List<BarVO> list = rows.getRecords().stream().map(BarVO::from).toList();
        return PageResult.of(list, rows.getTotal(), page, size);
    }

    @Override
    public Long createBar(BarCreateDTO dto) {
        String name = dto.getName().trim();
        // 名称全局唯一（uk_name 兜底），提前查一次给出友好提示
        Long exists = barMapper.selectCount(new LambdaQueryWrapper<Bar>().eq(Bar::getName, name));
        if (exists != null && exists > 0) {
            throw BusinessException.param("该吧名称已存在");
        }
        Bar bar = new Bar();
        bar.setId(redisIdWorker.nextId("bar"));
        bar.setName(name);
        bar.setDescription(dto.getDescription());
        bar.setCover(dto.getCover());
        bar.setCreatorId(UserContext.get());
        bar.setMemberCount(0);
        bar.setPostCount(0);
        bar.setStatus(1);
        barMapper.insert(bar);
        log.info("管理员创建贴吧 barId={}, name={}", bar.getId(), name);
        return bar.getId();
    }

    @Override
    public void hideBar(Long barId) {
        Bar bar = requireBar(barId);
        if (bar.getStatus() != null && bar.getStatus() == 0) {
            throw BusinessException.param("该吧已处于隐藏状态");
        }
        updateBarStatus(barId, 0);
        barService.evictBarCache(barId);
        // 热吧榜是 ZSet，改状态不会自动移除，需显式清理
        stringRedisTemplate.opsForZSet().remove(RedisKeyConstants.RANK_HOT_BAR, String.valueOf(barId));
        // 该吧下帖子的详情缓存也必须清掉：否则缓存里仍是"可见"的旧结果，
        // 会让隐藏对该帖失效（最长持续一个详情缓存 TTL）
        evictBarPostsCache(barId);
        log.info("管理员隐藏贴吧 barId={}, name={}", barId, bar.getName());
    }

    @Override
    public void restoreBar(Long barId) {
        Bar bar = requireBar(barId);
        if (bar.getStatus() != null && bar.getStatus() == 1) {
            throw BusinessException.param("该吧已是正常状态");
        }
        updateBarStatus(barId, 1);
        barService.evictBarCache(barId);
        // 同理：隐藏期间详情接口可能已写入"空值缓存"（防穿透），
        // 不清理会导致恢复后仍返回 2002，最长 5 分钟
        evictBarPostsCache(barId);
        log.info("管理员恢复贴吧 barId={}, name={}", barId, bar.getName());
    }

    /**
     * 清除某吧下全部帖子的详情缓存与重建锁，并顺带清首页列表缓存。
     *
     * <p>吧的可见性会连带影响其下所有帖子（列表 SQL 用 {@code b.status = 1}，
     * 详情走内存判定），因此吧状态一变，这些帖子的详情缓存全部失效。</p>
     *
     * <p>注：仅按 ID 查询以减少数据量；批量 DEL 在帖子极多时会阻塞 Redis 单线程，
     * 生产可改为 UNLINK 或按批处理。</p>
     */
    private void evictBarPostsCache(Long barId) {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .select(Post::getId)
                .eq(Post::getBarId, barId));
        if (!posts.isEmpty()) {
            List<String> keys = new ArrayList<>(posts.size() * 2);
            for (Post p : posts) {
                keys.add(RedisKeyConstants.POST_CACHE + p.getId());
                keys.add(RedisKeyConstants.POST_CACHE + p.getId() + ":lock");
            }
            stringRedisTemplate.delete(keys);
        }
        evictHomeListCache();
    }

    @Override
    public void deleteBar(Long barId) {
        Bar bar = requireBar(barId);

        // 1) 该吧下全部帖子（每帖再走 deletePost 的完整清理：楼层/点赞/收藏 + Redis 残留）
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>().eq(Post::getBarId, barId));
        for (Post post : posts) {
            deletePost(post.getId());
        }

        // 2) 指向该吧的关注关系：吧已不存在，这些记录再无意义
        followMapper.delete(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowBarId, barId));

        // 3) 关联的秒杀活动：连同订单与 Redis 库存/一人一单标记一并清理
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().eq(Activity::getBarId, barId));
        for (Activity activity : activities) {
            purgeActivity(activity.getId());
        }

        // 4) 吧主记录
        barMapper.deleteById(barId);

        // 5) 吧相关 Redis：信息缓存、关注计数、热吧榜成员、首页列表缓存
        barService.evictBarCache(barId);
        stringRedisTemplate.delete(RedisKeyConstants.BAR_MEMBER + barId);
        stringRedisTemplate.opsForZSet().remove(RedisKeyConstants.RANK_HOT_BAR, String.valueOf(barId));
        evictHomeListCache();

        log.warn("管理员物理删除贴吧 barId={}, name={}；级联删除 帖子 {} 篇、活动 {} 个、关注关系若干",
                barId, bar.getName(), posts.size(), activities.size());
    }

    /**
     * 清理单个秒杀活动：订单表 + Redis 库存与一人一单标记 + 活动主记录。
     * <p>不做这步，删除吧之后首页的「限量徽章 / 抢楼」入口仍会指向一个
     * 所属吧已不存在的活动。</p>
     */
    private void purgeActivity(Long activityId) {
        // 先记下领取人：订单删掉之后就查不到该给谁失效徽章缓存了
        List<Long> awardedUsers = activityOrderMapper.selectList(
                        new LambdaQueryWrapper<ActivityOrder>()
                                .select(ActivityOrder::getUserId)
                                .eq(ActivityOrder::getActivityId, activityId))
                .stream().map(ActivityOrder::getUserId).distinct().toList();

        activityOrderMapper.delete(new LambdaQueryWrapper<ActivityOrder>()
                .eq(ActivityOrder::getActivityId, activityId));
        stringRedisTemplate.delete(RedisKeyConstants.SECKILL_STOCK + activityId);
        // 一人一单标记按用户分散：dbd:seckill:order:{activityId}:{userId}
        removeKeysByPattern(RedisKeyConstants.SECKILL_ORDER + activityId + ":*");
        activityMapper.deleteById(activityId);
        // 徽章随活动一起消失，缓存不清的话最长 10 分钟内作者昵称旁还挂着它
        awardedUsers.forEach(badgeService::evict);
    }

    /**
     * 按 pattern 批量删除 key。
     * <p>使用 SCAN 游标而非 KEYS：KEYS 会阻塞 Redis 单线程，key 多时直接拖垮服务。</p>
     */
    private void removeKeysByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(200).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /* ==================== 限量徽章活动管理 ==================== */

    @Override
    public PageResult<ActivityVO> activityList(String keyword, Integer status, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Activity::getBadgeName, kw).or().like(Activity::getTitle, kw));
        }
        applyStatusFilter(qw, status);
        qw.orderByDesc(Activity::getBeginTime).orderByDesc(Activity::getId);
        IPage<Activity> rows = activityMapper.selectPage(new Page<>(page, size), qw);
        // 复用秒杀服务的 VO 口径：实时剩余库存 / 已抢数量 / 是否已抢
        return PageResult.of(activityService.toVOList(rows.getRecords()), rows.getTotal(), page, size);
    }

    /**
     * 按<b>时间</b>而非 DB status 列筛选：status 列只在创建/结束时写入，
     * 活动自然到期不会回写，用时间判断才能与列表展示的动态状态一致
     * （否则会出现"筛进行中却筛不出来"）。
     */
    private void applyStatusFilter(LambdaQueryWrapper<Activity> qw, Integer status) {
        if (status == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (status == 0) {
            qw.gt(Activity::getBeginTime, now);
        } else if (status == 1) {
            qw.le(Activity::getBeginTime, now).ge(Activity::getEndTime, now);
        } else if (status == 2) {
            qw.lt(Activity::getEndTime, now);
        } else {
            throw BusinessException.param("状态取值不合法");
        }
    }

    @Override
    public Long createActivity(ActivityCreateDTO dto) {
        String badgeName = dto.getBadgeName().trim();
        LocalDateTime begin = parseTime(dto.getBeginTime(), "开始时间");
        LocalDateTime end = parseTime(dto.getEndTime(), "结束时间");
        if (!begin.isBefore(end)) {
            throw BusinessException.param("结束时间必须晚于开始时间");
        }
        requireBadgeNameAvailable(badgeName, null);

        Activity activity = new Activity();
        activity.setId(redisIdWorker.nextId("activity"));
        activity.setBarId(dto.getBarId());
        activity.setTitle("限量徽章：" + badgeName);
        activity.setType(TYPE_BADGE);
        activity.setBadgeName(badgeName);
        activity.setStock(dto.getStock());
        activity.setAwardDesc(resolveAwardDesc(dto.getAwardDesc(), dto.getStock()));
        activity.setBeginTime(begin);
        activity.setEndTime(end);
        activity.setStatus(begin.isAfter(LocalDateTime.now()) ? 0 : 1);
        activityMapper.insert(activity);
        // 不预热 Redis 库存：detail/list 在 key 不存在时会回退成 DB 总量（等价于"一个没抢"），
        // grab 里也有 setIfAbsent 兜底。少一次写就少一处不一致。
        log.info("管理员发布限量徽章 activityId={}, badgeName={}, stock={}, {} ~ {}",
                activity.getId(), badgeName, activity.getStock(), begin, end);
        return activity.getId();
    }

    @Override
    public void updateActivity(Long activityId, ActivityCreateDTO dto) {
        Activity old = requireActivity(activityId);
        String badgeName = dto.getBadgeName().trim();
        LocalDateTime begin = parseTime(dto.getBeginTime(), "开始时间");
        LocalDateTime end = parseTime(dto.getEndTime(), "结束时间");
        if (!begin.isBefore(end)) {
            throw BusinessException.param("结束时间必须晚于开始时间");
        }
        requireBadgeNameAvailable(badgeName, activityId);

        // 必须在改 DB 之前调整 Redis 库存：syncRedisStock 需要旧的总量来推算已抢数量
        syncRedisStock(activityId, old.getStock(), dto.getStock());

        Activity update = new Activity();
        update.setId(activityId);
        update.setBarId(dto.getBarId());
        update.setTitle("限量徽章：" + badgeName);
        update.setBadgeName(badgeName);
        update.setStock(dto.getStock());
        update.setAwardDesc(resolveAwardDesc(dto.getAwardDesc(), dto.getStock()));
        update.setBeginTime(begin);
        update.setEndTime(end);
        update.setStatus(begin.isAfter(LocalDateTime.now()) ? 0 : 1);
        activityMapper.updateById(update);
        log.info("管理员编辑限量徽章 activityId={}, badgeName={}, stock={}(原 {})",
                activityId, badgeName, dto.getStock(), old.getStock());
    }

    /**
     * 修改总量时同步 Redis 剩余库存，保持<b>已抢数量不变</b>。
     *
     * <p>若直接覆盖成新总量，已经抢走的份数会凭空变回可抢（超发）；
     * 若调低到低于已抢数量，则置 0 表示售罄——不能为负，否则 Lua 里
     * {@code stock <= 0} 判断仍会拒绝，但数值含义就错了。</p>
     */
    private void syncRedisStock(Long activityId, Integer oldStock, int newStock) {
        String key = RedisKeyConstants.SECKILL_STOCK + activityId;
        // key 不存在 = 从没人抢过（grab 里 setIfAbsent 才会创建），无需处理
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        String remainStr = stringRedisTemplate.opsForValue().get(key);
        int remain = remainStr == null ? (oldStock == null ? 0 : oldStock) : Integer.parseInt(remainStr);
        int awarded = Math.max(0, (oldStock == null ? 0 : oldStock) - remain);
        int newRemain = Math.max(0, newStock - awarded);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(newRemain));
        log.info("活动 {} 总量 {} → {}，已抢 {}，Redis 剩余库存同步为 {}",
                activityId, oldStock, newStock, awarded, newRemain);
    }

    @Override
    public void deleteActivity(Long activityId) {
        Activity activity = requireActivity(activityId);
        Long awarded = activityOrderMapper.selectCount(new LambdaQueryWrapper<ActivityOrder>()
                .eq(ActivityOrder::getActivityId, activityId));
        purgeActivity(activityId);
        log.warn("管理员物理删除活动 activityId={}, badgeName={}, 已发放 {} 枚，领取记录与徽章同步失效",
                activityId, activity.getBadgeName(), awarded);
    }

    @Override
    public void endActivity(Long activityId) {
        Activity activity = requireActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        // 双重判定：status=2 覆盖「刚刚结束、end_time 还在当前秒内」的情况
        //（DATETIME 是秒精度，写入时小数秒会被四舍五入，直接比时间会有最多 1 秒的窗口）；
        // end_time 已过期则覆盖「自然到期但 status 仍为 1」的情况——此时若继续把
        // end_time 改成 now，等于把已结束的活动重新拉回进行中。
        if ((activity.getStatus() != null && activity.getStatus() == 2)
                || (activity.getEndTime() != null && activity.getEndTime().isBefore(now))) {
            throw BusinessException.param("该活动已经结束了");
        }
        Activity update = new Activity();
        update.setId(activityId);
        // 不改 end_time 之外的字段：已抢到的人徽章照常保留（领取记录不动）
        update.setEndTime(now);
        update.setStatus(2);
        activityMapper.updateById(update);
        log.info("管理员提前结束活动 activityId={}, badgeName={}", activityId, activity.getBadgeName());
    }

    /** 称号全局唯一（DB uk_badge_name 兜底）；编辑时排除自己 */
    private void requireBadgeNameAvailable(String badgeName, Long excludeId) {
        Long count = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getBadgeName, badgeName)
                .ne(excludeId != null, Activity::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.param("已存在同名徽章活动：" + badgeName);
        }
    }

    /** 奖励说明留空时给一句默认文案，避免前台展示空行 */
    private String resolveAwardDesc(String input, int stock) {
        if (input != null && !input.isBlank()) {
            return input.trim();
        }
        return stock == 1 ? "全站唯一，仅此一枚" : "限量 " + stock + " 枚，先到先得";
    }

    /** 兼容前端可能传来的 ISO 形式（2026-09-12T20:00:00） */
    private LocalDateTime parseTime(String text, String field) {
        try {
            return LocalDateTime.parse(text.trim().replace('T', ' '), TIME_FMT);
        } catch (DateTimeParseException e) {
            throw BusinessException.param(field + "格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private Activity requireActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BusinessException.notFound("活动不存在");
        }
        return activity;
    }

    /* ==================== 工具 ==================== */

    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BusinessException.notFound("帖子不存在");
        }
        return post;
    }

    private Bar requireBar(Long barId) {
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            throw BusinessException.notFound("吧不存在");
        }
        return bar;
    }

    /** 只更新 status 字段，避免整对象覆盖（其余字段保持 DB 原值） */
    private void updatePostStatus(Long postId, int status) {
        Post update = new Post();
        update.setId(postId);
        update.setStatus(status);
        postMapper.updateById(update);
    }

    private void updateBarStatus(Long barId, int status) {
        Bar update = new Bar();
        update.setId(barId);
        update.setStatus(status);
        barMapper.updateById(update);
    }

    /**
     * 清理帖子在 Redis 中的全部残留：
     * 缓存类（详情/锁）、计数与集合类（点赞/收藏/楼层/浏览/UV）、索引类（GEO/热帖榜/Feed/首页列表）。
     */
    private void purgePostRedis(Long postId) {
        stringRedisTemplate.delete(List.of(
                RedisKeyConstants.POST_CACHE + postId,
                RedisKeyConstants.POST_CACHE + postId + ":lock",
                RedisKeyConstants.POST_LIKE + postId,
                RedisKeyConstants.POST_FAVORITE + postId,
                RedisKeyConstants.POST_FLOOR + postId,
                RedisKeyConstants.POST_VIEW + postId,
                RedisKeyConstants.POST_UV + postId
        ));
        String member = String.valueOf(postId);
        stringRedisTemplate.opsForGeo().remove(RedisKeyConstants.GEO_POST, member);
        stringRedisTemplate.opsForZSet().remove(RedisKeyConstants.RANK_HOT_POST, member);
        removeFromAllFeedTimelines(member);
        evictHomeListCache();
    }

    /**
     * 从所有用户的 Feed 时间线中移除该帖。
     * <p>Feed ZSet 的 member 是 postId，但 key 按用户分散，因此需要遍历
     * {@code dbd:feed:user:*}。这里用 SCAN 而非 KEYS——KEYS 会阻塞 Redis 单线程。</p>
     * <p>注：用户量很大时全量遍历仍偏重，生产更合适的是维护 postId→粉丝 反查索引，
     * 或改为读取时校验帖子存活（本项目 demo 规模下 SCAN 足够）。</p>
     */
    private void removeFromAllFeedTimelines(String member) {
        Set<String> feedKeys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisKeyConstants.FEED_USER + "*")
                .count(200)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                feedKeys.add(cursor.next());
            }
        }
        for (String key : feedKeys) {
            stringRedisTemplate.opsForZSet().remove(key, member);
        }
    }

    /** 清首页列表缓存 + 城市列表缓存（帖子/吧变动后均需调用，否则缓存 TTL 内仍是旧数据） */
    private void evictHomeListCache() {
        for (int p = 1; p <= HOME_LIST_CACHE_PAGES; p++) {
            stringRedisTemplate.delete(RedisKeyConstants.POST_LIST_HOME + p);
        }
        // 帖子/吧的增删或状态变化都会改变"各城市可见帖子数"，城市列表缓存一并失效
        stringRedisTemplate.delete(RedisKeyConstants.POST_CITIES);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 50);
    }
}
