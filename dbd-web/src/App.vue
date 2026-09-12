<script setup>
// 全局布局：顶部导航（登录后显示用户下拉：个人资料/我的主页/发帖/管理后台/退出）+ 页面出口 router-view
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './stores/user'

const route = useRoute()
const userStore = useUserStore()
// 登录页隐藏顶栏（保持登录卡片干净）
const showTopbar = computed(() => route.name !== 'login')
</script>

<template>
  <div class="app-shell">
    <header v-if="showTopbar" class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="logo">Day-<span>BY-</span>Day</router-link>
        <nav class="nav-links">
          <router-link to="/">首页</router-link>
          <router-link to="/feed">关注</router-link>
          <router-link to="/rank">排行</router-link>
          <router-link to="/nearby">同城</router-link>
          <router-link to="/search">搜索</router-link>
        </nav>
        <div class="topbar-right">
          <template v-if="userStore.token">
            <el-dropdown>
              <span class="user-entry">
                {{ userStore.userInfo?.nickname || '我' }}
                <el-tag v-if="userStore.isAdmin" type="danger" size="small" class="admin-badge">管理员</el-tag>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/profile')">个人资料</el-dropdown-item>
                  <el-dropdown-item @click="$router.push(`/user/${userStore.userInfo?.id || ''}`)">我的主页</el-dropdown-item>
                  <el-dropdown-item @click="$router.push('/post/new')">发帖</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" divided @click="$router.push('/admin')">管理后台</el-dropdown-item>
                  <el-dropdown-item divided @click="userStore.logout(); $router.push('/')">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else to="/login" class="login-btn">登录</router-link>
        </div>
      </div>
    </header>

    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; background: #f5f6f7; color: #333; }

.app-shell { min-height: 100vh; display: flex; flex-direction: column; }
.app-main { flex: 1; }

.topbar { background: #fff; border-bottom: 3px solid #4e6ef2; }
.topbar-inner { max-width: 1080px; margin: 0 auto; display: flex; align-items: center; height: 64px; padding: 0 12px; }
.logo { font-size: 26px; font-weight: bold; color: #4e6ef2; margin-right: 28px; letter-spacing: 1px; text-decoration: none; }
.logo span { color: #f40; }
.nav-links a { color: #333; text-decoration: none; font-size: 14px; margin-right: 18px; }
.nav-links a:hover { color: #4e6ef2; }
.topbar-right { margin-left: auto; display: flex; align-items: center; }
.user-entry { cursor: pointer; color: #4e6ef2; font-size: 14px; display: inline-flex; align-items: center; gap: 6px; }
.admin-badge { transform: scale(0.9); }
.login-btn { color: #4e6ef2; text-decoration: none; font-size: 14px; }
</style>
