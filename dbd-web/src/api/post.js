// 帖子模块接口（对应 API.md §3.2）
import request from '../utils/request'

/** 帖子列表 GET /api/post/list（可按 barId/userId/keyword 筛选） */
export function getPostList(params) {
  return request.get('/post/list', { params })
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

/** 楼层列表 GET /api/post/{id}/comments */
export function getComments(postId, params) {
  return request.get(`/post/${postId}/comments`, { params })
}

/** 回帖/盖楼 🔒 POST /api/post/{id}/comment */
export function addComment(postId, data) {
  return request.post(`/post/${postId}/comment`, data)
}
