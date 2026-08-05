// Axios 封装：
// 1) 请求拦截器自动携带 Authorization: Bearer <token>
// 2) 响应拦截器解包统一响应 {code, msg, data}；code != 1 弹错误提示并 reject
// 3) HTTP 401 统一清理登录态并跳转登录页（带 redirect 回跳）
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'

// 开发期由 Vite 代理 /api → localhost:8080；生产由 nginx 反代 /api → 127.0.0.1:8080
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：注入登录 token
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器：统一解包 + 错误处理
request.interceptors.response.use(
  (response) => {
    const data = response.data
    // 业务失败（后端约定 code=1 成功）
    if (data && data.code !== undefined && data.code !== 1) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  (error) => {
    // 401：token 缺失/过期 → 清理并跳登录
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.warning('登录已过期，请重新登录')
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else {
      // 其他错误：展示后端 msg 或网络错误
      ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
