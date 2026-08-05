// 路由配置：所有页面懒加载；meta.requiresAuth = true 的页面未登录会跳转 /login（见底部守卫）
import { createRouter, createWebHistory } from 'vue-router'
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
  // 个人中心
  { path: '/user/:id', name: 'user', component: () => import('../views/UserView.vue') },
  // 搜索 + 热搜
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
  // 排行榜
  { path: '/rank', name: 'rank', component: () => import('../views/RankView.vue') },
  // 同城（GEO）
  { path: '/nearby', name: 'nearby', component: () => import('../views/NearbyView.vue') },
  // 抢楼 / 徽章秒杀
  { path: '/activity/:id', name: 'activity', component: () => import('../views/ActivityView.vue') },
  // 兜底 404
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：需登录页面未登录 → 跳登录页并记录回跳地址
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
