// 管理模块接口（对应 API.md §3.9）
// 整个 /api/admin/** 在后端由 AdminInterceptor 强制要求 role=1，
// 前端路由守卫只是体验优化，真正的鉴权以服务端为准。
import request from '../utils/request'

/* ---------- 帖子管理 ---------- */

/** 帖子管理列表（含隐藏/精华等全部状态）GET /api/admin/post/list */
export function getAdminPosts(params) {
  return request.get('/admin/post/list', { params })
}

/** 隐藏帖子（status=3，前台不可见，可恢复）POST /api/admin/post/{id}/hide */
export function hidePost(id) {
  return request.post(`/admin/post/${id}/hide`)
}

/** 恢复帖子（status=1）POST /api/admin/post/{id}/restore */
export function restorePost(id) {
  return request.post(`/admin/post/${id}/restore`)
}

/** 删除帖子（物理删除，不可恢复）DELETE /api/admin/post/{id} */
export function deletePost(id) {
  return request.delete(`/admin/post/${id}`)
}

/* ---------- 吧管理 ---------- */

/** 吧管理列表（含已隐藏的吧）GET /api/admin/bar/list */
export function getAdminBars(params) {
  return request.get('/admin/bar/list', { params })
}

/** 创建贴吧 POST /api/admin/bar */
export function createBar(data) {
  return request.post('/admin/bar', data)
}

/** 隐藏贴吧（status=0）POST /api/admin/bar/{id}/hide */
export function hideBar(id) {
  return request.post(`/admin/bar/${id}/hide`)
}

/** 恢复贴吧（status=1）POST /api/admin/bar/{id}/restore */
export function restoreBar(id) {
  return request.post(`/admin/bar/${id}/restore`)
}

/** 删除贴吧（物理删除，级联删除其下全部帖子）DELETE /api/admin/bar/{id} */
export function deleteBar(id) {
  return request.delete(`/admin/bar/${id}`)
}

/* ---------- 限量徽章活动管理 ---------- */

/** 活动管理列表（含未开始/已结束）GET /api/admin/activity/list */
export function getAdminActivities(params) {
  return request.get('/admin/activity/list', { params })
}

/** 发布限量徽章活动 POST /api/admin/activity */
export function createActivity(data) {
  return request.post('/admin/activity', data)
}

/** 编辑限量徽章活动（保持已抢数量不变）PUT /api/admin/activity/{id} */
export function updateActivity(id, data) {
  return request.put(`/admin/activity/${id}`, data)
}

/** 提前结束活动（已抢到的徽章保留）POST /api/admin/activity/{id}/end */
export function endActivity(id) {
  return request.post(`/admin/activity/${id}/end`)
}

/** 删除活动（物理删除，领取记录与徽章一并失效）DELETE /api/admin/activity/{id} */
export function deleteActivity(id) {
  return request.delete(`/admin/activity/${id}`)
}
