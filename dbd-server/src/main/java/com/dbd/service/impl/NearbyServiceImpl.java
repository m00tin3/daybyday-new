package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.common.BusinessException;
import com.dbd.entity.Bar;
import com.dbd.entity.Post;
import com.dbd.entity.User;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.mapper.UserMapper;
import com.dbd.service.NearbyService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 同城服务实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 9）：
 * <ul>
 *   <li>发帖带坐标时 GEOADD 写入 {@code dbd:geo:post}（member=postId，坐标=经纬度）</li>
 *   <li>查询走 GEORADIUS：按坐标 + 半径检索，带距离、按距离升序
 *       （兼容 Redis 3.2+；Redis 6.2+ 可等价升级为 GEOSEARCH）</li>
 *   <li>懒构建兜底：GEO 集合为空（首次访问/Redis 重启）时从 DB 带坐标帖子重建</li>
 * </ul>
 */
@Slf4j
@Service
public class NearbyServiceImpl implements NearbyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final BarMapper barMapper;

    public NearbyServiceImpl(StringRedisTemplate stringRedisTemplate, PostMapper postMapper,
                             UserMapper userMapper, BarMapper barMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.barMapper = barMapper;
    }

    @Override
    public List<PostVO> nearby(Double x, Double y, Integer distance) {
        if (x == null || y == null) {
            throw BusinessException.param("经纬度不能为空");
        }
        int radius = distance == null || distance < 1 ? 5000 : distance;

        // 懒构建兜底：GEO 集合为空（首次访问/Redis 重启）时从 DB 带坐标帖子重建
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(RedisKeyConstants.GEO_POST))) {
            rebuildGeo();
        }

        // GEORADIUS：以 (x, y) 为圆心、radius 米为半径，返回带距离、按距离升序
        // （GEORADIUS 兼容 Redis 3.2+；Redis 6.2+ 可等价升级为 GEOSEARCH）
        GeoResults<GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                RedisKeyConstants.GEO_POST,
                new Circle(new Point(x, y), new Distance(radius, Metrics.NEUTRAL)),
                GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending());

        if (results == null || results.getContent() == null || results.getContent().isEmpty()) {
            return List.of();
        }

        List<GeoResultHolder> holders = new ArrayList<>();
        for (var r : results.getContent()) {
            String member = r.getContent() == null ? null : r.getContent().getName();
            if (member == null) {
                continue;
            }
            double dist = r.getDistance() == null ? 0 : r.getDistance().getValue();
            holders.add(new GeoResultHolder(Long.valueOf(member), dist));
        }

        List<Long> postIds = holders.stream().map(GeoResultHolder::postId).toList();
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        Set<Long> userIds = postMap.values().stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = postMap.values().stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, String> barNameMap = barIds.isEmpty() ? Map.of()
                : barMapper.selectBatchIds(barIds).stream()
                        .collect(Collectors.toMap(Bar::getId, Bar::getName));

        List<PostVO> list = new ArrayList<>();
        for (GeoResultHolder h : holders) {
            Post post = postMap.get(h.postId());
            if (post == null || post.getStatus() == 0) {
                continue;
            }
            PostVO vo = PostVO.from(post, userMap.get(post.getUserId()), barNameMap.get(post.getBarId()));
            vo.setDistance(Math.round(h.distance() * 10) / 10.0); // 保留 1 位小数
            list.add(vo);
        }
        return list;
    }

    /** 懒构建：DB 中带坐标的可见帖子（正常+精华）全量 GEOADD（Redis 重启/首次访问后自动恢复） */
    private void rebuildGeo() {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, 1, 2)
                .isNotNull(Post::getLongitude)
                .isNotNull(Post::getLatitude));
        for (Post p : posts) {
            stringRedisTemplate.opsForGeo().add(RedisKeyConstants.GEO_POST,
                    new Point(p.getLongitude(), p.getLatitude()), String.valueOf(p.getId()));
        }
        log.info("GEO 懒构建完成，共 {} 帖", posts.size());
    }

    /** GEO 结果临时载体 */
    private record GeoResultHolder(Long postId, double distance) {
    }
}
