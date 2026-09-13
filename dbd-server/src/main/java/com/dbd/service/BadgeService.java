package com.dbd.service;

import com.dbd.vo.BadgeVO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 限量徽章服务：徽章墙查询 + 作者徽章批量填充。
 *
 * <p>徽章不单独建表，数据源是 {@code activity_order JOIN activity}（仅 type=2 且
 * 称号非空）。徽章会出现在帖子列表、楼层列表的作者昵称旁，这些列表一页可能涉及
 * 几十个不同作者，因此统一走 {@link #badgeNamesOf} 的**批量查询 + 用户维度缓存**，
 * 严禁在单个 VO 上逐个查库。</p>
 */
public interface BadgeService {

    /**
     * 某个用户的徽章墙（按获得时间倒序）。
     *
     * @param userId 用户ID
     * @return 徽章列表；无徽章返回空列表（不返回 null）
     */
    List<BadgeVO> userBadges(Long userId);

    /**
     * 批量取多个用户的徽章称号，供列表页一次性填充。
     *
     * @param userIds 用户ID集合（可为空，返回空 Map）
     * @return userId → 徽章称号列表（按获得时间倒序）；查不到的用户不会出现在 Map 里
     */
    Map<Long, List<String>> badgeNamesOf(Collection<Long> userIds);

    /** 给单个作者的 VO 填充徽章（用户主页、登录返回的 userInfo） */
    void fillAuthor(UserVO author);

    /** 给一批作者的 VO 填充徽章 */
    void fillAuthors(Collection<UserVO> authors);

    /** 给一批帖子的作者填充徽章 */
    void fillPostAuthors(Collection<PostVO> posts);

    /** 给一批楼层的作者填充徽章 */
    void fillCommentAuthors(Collection<CommentVO> comments);

    /**
     * 清除某用户的徽章缓存。
     * <p>领取成功后由 {@link SeckillOrderPersistService} 在**订单真正落库之后**调用；
     * 若在落库前就删，紧随其后的读请求会把"还没有徽章"的空结果重新缓存进去。</p>
     */
    void evict(Long userId);
}
