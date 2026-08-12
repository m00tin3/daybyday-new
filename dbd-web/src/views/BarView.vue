<script setup>
// 吧主页：吧信息 + 签到/关注 + 吧内帖子（对应 API.md §3.3）
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBarInfo, getBarPosts, signIn, followBar } from '../api/bar'
import { useUserStore } from '../stores/user'
import PostCard from '../components/PostCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const barId = route.params.id
const bar = ref(null)
const posts = ref([])
const loading = ref(true)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const signing = ref(false)

function needLogin() {
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return true
  }
  return false
}

async function load() {
  loading.value = true
  try {
    const res = await getBarInfo(barId)
    bar.value = res.data
  } catch {
    bar.value = null
  }
  try {
    const res = await getBarPosts(barId, { page: page.value, size: size.value })
    posts.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    posts.value = []
  } finally {
    loading.value = false
  }
}

// 签到：BitMap 接口，成功后刷新吧信息
async function doSign() {
  if (needLogin()) return
  signing.value = true
  try {
    const res = await signIn(barId)
    ElMessage.success(`签到成功！连续签到 ${res.data.signedDays} 天，本月累计 ${res.data.signCount} 天`)
    if (res.data.award) ElMessage.success(res.data.award)
    bar.value.signedToday = true
  } catch { /* 4004 已签到等，拦截器提示 */ } finally {
    signing.value = false
  }
}

// 关注/取消关注（幂等切换）
async function toggleFollow() {
  if (needLogin()) return
  try {
    const res = await followBar(barId)
    bar.value.isFollowed = res.data.isFollowed
    bar.value.memberCount = res.data.memberCount
    ElMessage.success(res.data.isFollowed ? '已关注' : '已取消关注')
  } catch { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<template>
  <div class="bar-page" v-loading="loading">
    <template v-if="bar">
      <!-- 吧信息卡 -->
      <div class="bar-card">
        <div class="bar-avatar">{{ bar.name.slice(0, 1) }}</div>
        <div class="bar-info">
          <h2>{{ bar.name }}</h2>
          <p class="desc">{{ bar.description || '这个吧还没有简介' }}</p>
          <p class="counts">关注 {{ bar.memberCount }} · 帖子 {{ bar.postCount }}</p>
        </div>
        <div class="bar-actions">
          <el-button type="primary" round :disabled="bar.signedToday" :loading="signing" @click="doSign">
            {{ bar.signedToday ? '今日已签到' : '签到' }}
          </el-button>
          <el-button round :type="bar.isFollowed ? 'info' : 'primary'" plain @click="toggleFollow">
            {{ bar.isFollowed ? '已关注' : '+ 关注' }}
          </el-button>
        </div>
      </div>

      <!-- 帖子列表 -->
      <div class="post-list">
        <div class="list-head">
          <h3>吧内帖子</h3>
          <el-button type="primary" size="small" @click="$router.push('/post/new')">发新帖</el-button>
        </div>
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
        <el-pagination
          v-if="total > size"
          class="pager"
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="(p) => { page = p; load() }"
        />
        <el-empty v-if="posts.length === 0" description="吧内还没有帖子" />
      </div>
    </template>
    <el-empty v-else-if="!loading" description="吧不存在">
      <el-button type="primary" @click="$router.push('/')">回首页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.bar-page { max-width: 1080px; margin: 16px auto; }
.bar-card { background: #fff; border-radius: 6px; padding: 20px; display: flex; align-items: center; gap: 16px; }
.bar-avatar { width: 64px; height: 64px; border-radius: 12px; background: #4e6ef2; color: #fff; font-size: 30px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.bar-info { flex: 1; }
.bar-info h2 { margin-bottom: 6px; }
.desc { color: #999; font-size: 13px; margin-bottom: 6px; }
.counts { color: #aaa; font-size: 12px; }
.bar-actions { display: flex; gap: 8px; }
.post-list { background: #fff; border-radius: 6px; padding: 8px 0; margin-top: 16px; }
.list-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 18px; border-bottom: 1px solid #f0f0f0; }
.list-head h3 { font-size: 14px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.pager { padding: 12px 18px; justify-content: center; }
</style>
