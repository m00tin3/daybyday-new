package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
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
import com.dbd.service.BarService;
import com.dbd.service.PostService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostRow;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    public AdminServiceImpl(PostMapper postMapper, BarMapper barMapper, CommentMapper commentMapper,
                            PostLikeMapper postLikeMapper, PostFavoriteMapper postFavoriteMapper,
                            FollowMapper followMapper, ActivityMapper activityMapper,
                            ActivityOrderMapper activityOrderMapper,
                            StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker,
                            PostService postService, BarService barService) {
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
    }

    /* ==================== 帖子管理 ==================== */

    @Override
    public PageResult<PostVO> postList(String keyword, Integer status, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        // 管理端查询不限制 status，可看到隐藏(3)与精华(2)
        IPage<PostRow> rows = postMapper.selectAdminPostPage(new Page<>(page, size), keyword, status);
        List<PostVO> list = rows.getRecords().stream().map(PostVO::fromRow).toList();
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
        activityOrderMapper.delete(new LambdaQueryWrapper<ActivityOrder>()
                .eq(ActivityOrder::getActivityId, activityId));
        stringRedisTemplate.delete(RedisKeyConstants.SECKILL_STOCK + activityId);
        // 一人一单标记按用户分散：dbd:seckill:order:{activityId}:{userId}
        removeKeysByPattern(RedisKeyConstants.SECKILL_ORDER + activityId + ":*");
        activityMapper.deleteById(activityId);
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
