// 关注 Feed 流接口（对应 API.md §3.5.2）
import request from '../utils/request'

/** 关注时间线滚动分页 GET /api/feed?lastId=&size= */
export function getFeed(params) {
  return request.get('/feed', { params })
}
