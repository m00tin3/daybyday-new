// 吧模块接口（对应 API.md §3.3）
import request from '../utils/request'

/** 吧信息 GET /api/bar/{id} */
export function getBarInfo(id) {
  return request.get(`/bar/${id}`)
}

/** 吧内帖子 GET /api/bar/{id}/posts */
export function getBarPosts(id, params) {
  return request.get(`/bar/${id}/posts`, { params })
}

/** 吧签到（BitMap）🔒 POST /api/bar/{id}/sign */
export function signIn(barId) {
  return request.post(`/bar/${barId}/sign`)
}

/** 关注/取消关注吧（幂等切换）🔒 POST /api/bar/{id}/follow */
export function followBar(id) {
  return request.post(`/bar/${id}/follow`)
}

/** 热吧榜（ZSet）GET /api/bar/rank */
export function getBarRank() {
  return request.get('/bar/rank')
}
