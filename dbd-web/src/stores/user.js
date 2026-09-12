// 用户状态：token + 用户信息，持久化到 localStorage（键 dbd_token / dbd_user）
// 登录/退出由 LoginView 与 App.vue 调用；request.js 与路由守卫读取 token
import { defineStore } from 'pinia'

const TOKEN_KEY = 'dbd_token'
const USER_KEY = 'dbd_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    // 登录令牌（Authorization: Bearer <token>）
    token: localStorage.getItem(TOKEN_KEY) || '',
    // 当前用户信息（UserVO：id/nickname/icon/signText/role）
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    /**
     * 是否管理员（role=1）。
     * 仅用于前端显示"管理后台"入口与路由守卫，**真正的鉴权在后端**
     * （/api/admin/** 由 AdminInterceptor 校验），因此本地缓存过期不会造成越权。
     */
    isAdmin: (state) => state.userInfo?.role === 1
  },
  actions: {
    /** 登录成功写入（对应 POST /api/auth/login 的 data） */
    setLogin({ token, userInfo }) {
      this.token = token
      this.userInfo = userInfo
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
    },
    /** 局部更新用户信息（资料修改成功后同步本地缓存，避免刷新前后不一致） */
    setUserInfo(userInfo) {
      this.userInfo = { ...(this.userInfo || {}), ...userInfo }
      localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
    },
    /** 退出登录：清内存 + 本地存储（401 拦截器也会调用） */
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
