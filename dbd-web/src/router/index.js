// 路由配置：所有页面懒加载；meta.requiresAuth = true 的页面未登录会跳转 /login（见底部守卫）
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const routes = [
  // 首页：帖子列表 + 热门吧（对应 API.md §3.2.1 / §3.3.5）
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  // 发帖：需登录
  { path: '/post/new', name: 'post-new', component: () => import('../views/PostNewView.vue'), meta: { requiresAuth: true } },
  // 帖子详情：正文 + 楼层评论
  { path: '/post/:id', name: 'post-detail', component: () => import('../views/PostDetailView.vue') },
  // 登录 / 注册（验证码模式）
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  // 吧主页
  { path: '/bar/:id', name: 'bar', component: () => import('../views/BarView.vue') },
  // 个人中心（他人/自己的公开主页）
  { path: '/user/:id', name: 'user', component: () => import('../views/UserView.vue') },
  // 个人资料（查看 + 编辑）：需登录
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true } },
  // 管理后台：需登录且为管理员（前端仅为入口拦截，服务端 /api/admin/** 另有强制校验）
  { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  // 搜索 + 热搜
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
  // 排行榜
  { path: '/rank', name: 'rank', component: () => import('../views/RankView.vue') },
  // 关注 Feed 流：需登录
  { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue'), meta: { requiresAuth: true } },
  // 按城市浏览（替代已封存的 GEO 同城）
  { path: '/city', name: 'city', component: () => import('../views/CityView.vue') },
  // 原「同城」路由已随 GEO 功能一并封存：导航入口已移除，路由也下线。
  // 恢复方式：打开下面这行，并在 App.vue 导航与 HomeView 侧栏加回链接即可
  //（NearbyView.vue 与后端 /api/nearby 代码均保留未删）。
  // { path: '/nearby', name: 'nearby', component: () => import('../views/NearbyView.vue') },
  // 限量徽章抢夺活动广场（列表）
  { path: '/activity', name: 'activity-plaza', component: () => import('../views/ActivityListView.vue') },
  // 单个活动详情 + 抢夺（抢楼 / 限量徽章）
  { path: '/activity/:id', name: 'activity', component: () => import('../views/ActivityView.vue') },
  // 兜底 404
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：
//   1) 需登录页面未登录 → 跳登录页并记录回跳地址
//   2) 需要管理员的页面，非管理员 → 提示并回首页
//      （userInfo 来自 localStorage，可能滞后；越权最终由后端 AdminInterceptor 拦截）
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.warning('需要管理员权限')
    return { name: 'home' }
  }
})

export default router
