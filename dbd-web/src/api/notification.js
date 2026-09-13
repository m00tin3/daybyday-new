// 消息通知接口（对应 API.md §3.10）
// 「回复我的帖子 / 回复我的楼层 / 赞了我的帖子」统一封装成一条消息，用 type 区分。
import request from '../utils/request'

/** 我的通知列表 🔒 GET /api/notification */
export function getNotifications(params) {
  return request.get('/notification', { params })
}

/** 未读数（未登录返回 0）GET /api/notification/unread-count */
export function getUnreadCount() {
  return request.get('/notification/unread-count')
}

/**
 * 全部标记为已读 🔒 POST /api/notification/read-all
 * 由前端在进入通知页时显式调用 —— 刻意不让 GET 列表接口顺手改已读状态。
 */
export function readAllNotifications() {
  return request.post('/notification/read-all')
}
