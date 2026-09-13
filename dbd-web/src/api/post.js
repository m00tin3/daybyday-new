// 帖子模块接口（对应 API.md §3.2）
import request from '../utils/request'

/** 帖子列表 GET /api/post/list（可按 barId/userId/city/keyword 筛选） */
export function getPostList(params) {
  return request.get('/post/list', { params })
}

/**
 * 有帖子的城市列表（按帖子数降序）GET /api/post/cities
 * 用于「按城市浏览」页；后端带 Redis 缓存。
 */
export function getPostCities() {
  return request.get('/post/cities')
}

/** 帖子详情 GET /api/post/{id} */
export function getPostDetail(id) {
  return request.get(`/post/${id}`)
}

/** 发帖 🔒 POST /api/post */
export function createPost(data) {
  return request.post('/post', data)
}

/** 点赞/取消点赞（幂等切换）🔒 POST /api/post/{id}/like */
export function likePost(id) {
  return request.post(`/post/${id}/like`)
}

/** 收藏/取消收藏（幂等切换）🔒 POST /api/post/{id}/favorite */
export function favoritePost(id) {
  return request.post(`/post/${id}/favorite`)
}

/** 楼层列表 GET /api/post/{id}/comments（每层带第 1 页子回复 + 子回复总数） */
export function getComments(postId, params) {
  return request.get(`/post/${postId}/comments`, { params })
}

/** 某一层楼的子回复分页 GET /api/post/{id}/comment/{floorId}/replies（楼中楼翻页） */
export function getFloorReplies(postId, floorId, params) {
  return request.get(`/post/${postId}/comment/${floorId}/replies`, { params })
}

/** 回帖/盖楼 🔒 POST /api/post/{id}/comment */
export function addComment(postId, data) {
  return request.post(`/post/${postId}/comment`, data)
}

/** 删除自己的帖子（软删除，仅本人；删后整帖不可见）🔒 DELETE /api/post/{id} */
export function deleteOwnPost(id) {
  return request.delete(`/post/${id}`)
}

/** 删除自己的回复（软删除，仅本人；楼层内的楼中楼会保留可见）🔒 DELETE /api/post/{id}/comment/{commentId} */
export function deleteOwnComment(postId, commentId) {
  return request.delete(`/post/${postId}/comment/${commentId}`)
}
