// 未读消息数：只服务于顶栏导航上的红点。
//
// 单独开一个 store 而不是塞进 user.js：退出登录、进入通知页（自动已读）
// 这两处都要改它，放在导航栏里各写一份 v-if 会散掉。
import { defineStore } from 'pinia'
import { getUnreadCount } from '../api/notification'
import { useUserStore } from './user'

export const useNoticeStore = defineStore('notice', {
  state: () => ({
    unread: 0
  }),

  actions: {
    /** 拉一次未读数；未登录直接归零（后端对此也返回 0，不会报错） */
    async refresh() {
      const userStore = useUserStore()
      if (!userStore.token) {
        this.unread = 0
        return
      }
      try {
        const res = await getUnreadCount()
        this.unread = res.data?.count ?? 0
      } catch {
        // 红点不值得弹错误提示，静默归零即可
        this.unread = 0
      }
    },

    /** 进入通知页并已全部标为已读后调用，红点立即消失 */
    clear() {
      this.unread = 0
    }
  }
})
