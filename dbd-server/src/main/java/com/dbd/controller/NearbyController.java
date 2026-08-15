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
