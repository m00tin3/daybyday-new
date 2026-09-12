package com.dbd.controller;

import com.dbd.common.Result;
import com.dbd.service.NearbyService;
import com.dbd.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同城接口（对应 API.md §3.8，阶段三）。
 *
 * <p><b>当前状态：功能已封存（代码保留）</b></p>
 * <p>封存原因：GEO 需要地图 SDK 把地址转成经纬度，否则只能要求用户手输坐标，
 * 体验差且无法校验，因此发帖改为手动填写城市（见 {@code Post.city}），
 * 前端「同城」入口已移除。</p>
 * <p>本接口本身未删除、仍可调用，但发帖不再写入 GEO 索引，
 * 因此除历史带坐标的帖子外不会有新数据。恢复步骤：前端加回入口即可，
 * 后端无需改动。</p>
 */
@Tag(name = "同城模块")
@RestController
@RequestMapping("/api/nearby")
public class NearbyController {

    private final NearbyService nearbyService;

    public NearbyController(NearbyService nearbyService) {
        this.nearbyService = nearbyService;
    }

    @Operation(summary = "附近帖子（GEO GEOSEARCH，按距离升序）")
    @GetMapping("/post")
    public Result<List<PostVO>> nearby(@RequestParam Double x,
                                       @RequestParam Double y,
                                       @RequestParam(required = false) Integer distance) {
        return Result.ok(nearbyService.nearby(x, y, distance));
    }
}
