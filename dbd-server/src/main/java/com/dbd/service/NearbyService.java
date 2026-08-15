package com.dbd.service;

import com.dbd.vo.PostVO;

import java.util.List;

/**
 * 同城服务：附近帖子（GEO，对应 API.md §3.8，阶段三）。
 */
public interface NearbyService {

    /** 附近帖子：GEO 按坐标 + 半径检索，返回按距离升序的 PostVO（含 distance） */
    List<PostVO> nearby(Double x, Double y, Integer distance);
}
