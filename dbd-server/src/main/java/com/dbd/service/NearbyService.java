package com.dbd.service;

import com.dbd.vo.PostVO;

import java.util.List;

/**
 * 同城服务：附近帖子（GEO，对应 API.md §3.8，阶段三）。
 *
 * <p><b>功能已封存、代码保留</b>：发帖改为手动填写城市后不再写入 GEO 索引，
 * 本服务仅供将来接入地图 SDK 时恢复使用，详见
 * {@link com.dbd.controller.NearbyController} 的说明。</p>
 */
public interface NearbyService {

    /** 附近帖子：GEO 按坐标 + 半径检索，返回按距离升序的 PostVO（含 distance） */
    List<PostVO> nearby(Double x, Double y, Integer distance);
}
