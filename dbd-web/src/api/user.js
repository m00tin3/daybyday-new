// 用户模块接口（对应 API.md §3.4）
import request from '../utils/request'

/** 用户主页信息 GET /api/user/{id} */
export function getUserProfile(id) {
  return request.get(`/user/${id}`)
}

/** 修改个人资料（仅本人）🔒 PUT /api/user/profile */
export function updateUserProfile(data) {
  return request.put('/user/profile', data)
}

/** 用户帖子 GET /api/user/{id}/posts */
export function getUserPosts(id, params) {
  return request.get(`/user/${id}/posts`, { params })
}

/** 某人的回复列表（所有人可见）GET /api/user/{id}/replies */
export function getUserReplies(id, params) {
  return request.get(`/user/${id}/replies`, { params })
}

/** 我的收藏（仅本人）🔒 GET /api/user/favorites */
export function getUserFavorites(params) {
  return request.get('/user/favorites', { params })
}

/** 签到日历（BitMap 查询）GET /api/user/{id}/sign */
export function getSignCalendar(userId) {
  return request.get(`/user/${userId}/sign`)
}

/** 关注/取消关注用户（幂等切换）🔒 POST /api/user/{id}/follow */
export function followUser(id) {
  return request.post(`/user/${id}/follow`)
}

/** 热帖榜（ZSet）GET /api/rank/hot/post */
export function getHotPosts() {
  return request.get('/rank/hot/post')
}
