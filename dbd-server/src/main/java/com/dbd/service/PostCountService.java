package com.dbd.service;

import com.dbd.vo.PostVO;

import java.util.List;

/**
 * 帖子计数回填：把 {@link PostVO} 里的点赞/收藏数换成 Redis 里的实时值。
 *
 * <p><b>为什么需要这个</b>：{@code post.like_count} / {@code post.favorite_count}
 * 两列自建表起就没有任何代码写过（发帖时置 0 后再没人更新），真实计数只存在
 * Redis 的 Set（{@code dbd:post:like:{id}} / {@code dbd:post:favorite:{id}}）里。
 * 而 {@code PostVO.fromRow} / {@code from} 读的正是那两列，于是前台无论列表还是详情
 * 恒显示 0 赞。详情页的浏览/UV 之所以是对的，是因为 {@code PostServiceImpl.countView}
 * 每请求都用 Redis 覆盖了一次 —— 点赞/收藏当时漏了这一步。</p>
 *
 * <p>本类把这一步补齐，且列表也走同一套：列表一页最多 50 条，逐个 {@code SCARD}
 * 就是 N+1，因此用一次 pipeline 取完（同 {@code ActivityServiceImpl} 用 MGET 批量取秒杀库存的思路）。</p>
 *
 * <p>独立成组件而不是挂在 {@code PostService} 上，是因为 {@code FeedServiceImpl}
 * 也要用，而 {@code PostServiceImpl} 已经依赖 {@code FeedService}——挂过去会形成循环依赖。</p>
 */
public interface PostCountService {

    /**
     * 就地把 {@code posts} 里每条帖子的 likeCount / favoriteCount 覆盖为 Redis 实时值。
     * <p>空集合安全；Redis 里没有对应 key 时按 0 处理（等价于"一个赞都没有"）。</p>
     */
    void fill(List<PostVO> posts);
}
