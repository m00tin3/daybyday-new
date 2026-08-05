// 活动/搜索/同城模块接口（对应 API.md §3.6 / §3.7 / §3.8）
import request from '../utils/request'

/** 秒杀活动详情 GET /api/activity/{id} */
export function getActivityInfo(id) {
  return request.get(`/activity/${id}`)
}

/** 抢楼/领取徽章（Lua 秒杀）🔒 POST /api/activity/{id}/grab */
export function grabActivity(id) {
  return request.post(`/activity/${id}/grab`)
}

/** 热搜词（ZSet）GET /api/search/hot */
export function getHotSearch() {
  return request.get('/search/hot')
}

/** 搜索帖子 GET /api/search/post */
export function searchPosts(keyword, params) {
  return request.get('/search/post', { params: { keyword, ...params } })
}

/** 附近帖子（GEO）GET /api/nearby/post */
export function getNearbyPosts(params) {
  return request.get('/nearby/post', { params })
}
