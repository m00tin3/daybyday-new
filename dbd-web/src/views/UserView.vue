<script setup>
// 个人中心：主页信息 + 关注按钮 + 签到日历 + 帖子/收藏 Tab（对应 API.md §3.4）
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserProfile, getUserPosts, getUserFavorites, getSignCalendar, followUser } from '../api/user'
import { getUserBadges } from '../api/activity'
import { useUserStore } from '../stores/user'
import PostCard from '../components/PostCard.vue'
import BadgeWall from '../components/BadgeWall.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userId = route.params.id
const profile = ref(null)
const tab = ref('posts')
const posts = ref([])
const favorites = ref([])
const badges = ref([])
const signInfo = ref({ signList: [], signCount: 0 })
const loading = ref(false)

// 本月日历：42 格（6 行 × 7 列）
const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1
const firstWeekday = new Date(year, month - 1, 1).getDay()
const daysInMonth = new Date(year, month, 0).getDate()
const calendar = computed(() => {
  const cells = []
  for (let i = 0; i < firstWeekday; i++) cells.push({ day: null })
  for (let d = 1; d <= daysInMonth; d++) cells.push({ day: d, signed: signInfo.value.signList.includes(d), today: d === now.getDate() })
  return cells
})

const isSelf = computed(() => userStore.userInfo && String(userStore.userInfo.id) === String(userId))
// 头像：填的是图片地址就渲染图片，否则按文本（emoji 等）展示
const isUrl = (v) => typeof v === 'string' && /^https?:\/\//i.test(v)

async function load() {
  loading.value = true
  try {
    const res = await getUserProfile(userId)
    profile.value = res.data
  } catch {
    profile.value = null
  }
  await Promise.all([loadPosts(), loadSign(), loadBadges()])
  loading.value = false
}

async function loadBadges() {
  try {
    const res = await getUserBadges(userId)
    badges.value = res.data ?? []
  } catch { badges.value = [] }
}

async function loadPosts() {
  try {
    const res = await getUserPosts(userId, { page: 1, size: 20 })
    posts.value = res.data?.list ?? []
  } catch { posts.value = [] }
}

async function loadFavorites() {
  try {
    const res = await getUserFavorites({ page: 1, size: 20 })
    favorites.value = res.data?.list ?? []
  } catch { favorites.value = [] }
}

async function loadSign() {
  try {
    const res = await getSignCalendar(userId)
    signInfo.value = res.data ?? { signList: [], signCount: 0 }
  } catch { signInfo.value = { signList: [], signCount: 0 } }
}

function switchTab(t) {
  tab.value = t
  if (t === 'favorites' && favorites.value.length === 0) loadFavorites()
}

async function toggleFollow() {
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const res = await followUser(userId)
    profile.value.isFollowed = res.data.isFollowed
    profile.value.followerCount = res.data.followerCount
    ElMessage.success(res.data.isFollowed ? '已关注' : '已取消关注')
  } catch { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<template>
  <div class="user-page" v-loading="loading">
    <template v-if="profile">
      <!-- 用户信息卡 -->
      <div class="profile-card">
        <div class="avatar">
          <img v-if="isUrl(profile.user?.icon)" :src="profile.user.icon" alt="头像" />
          <span v-else>{{ profile.user?.icon || '👤' }}</span>
        </div>
        <div class="info">
          <h2>{{ profile.user?.nickname }}</h2>
          <p class="sign">{{ profile.user?.signText || '这个人很懒，什么都没写' }}</p>
          <p class="counts">
            发帖 {{ profile.postCount }} · 粉丝 {{ profile.followerCount }} · 关注 {{ profile.followingCount }}
          </p>
        </div>
        <div class="actions">
          <el-button v-if="isSelf" round @click="$router.push('/profile')">编辑资料</el-button>
          <el-button v-else round :type="profile.isFollowed ? 'info' : 'primary'" @click="toggleFollow">
            {{ profile.isFollowed ? '已关注' : '+ 关注' }}
          </el-button>
        </div>
      </div>

      <div class="body-grid">
        <!-- 左侧内容区 -->
        <div class="content-area">
          <el-tabs v-model="tab" @tab-change="switchTab">
            <el-tab-pane label="TA 的帖子" name="posts">
              <PostCard v-for="p in posts" :key="p.id" :post="p" />
              <el-empty v-if="posts.length === 0" description="还没有发过帖" />
            </el-tab-pane>
            <el-tab-pane v-if="isSelf" label="我的收藏" name="favorites">
              <PostCard v-for="p in favorites" :key="p.id" :post="p" />
              <el-empty v-if="favorites.length === 0" description="还没有收藏" />
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- 右侧：徽章墙 + 签到日历 -->
        <div class="side-area">
          <BadgeWall :badges="badges" :show-empty-hint="isSelf" class="side-card" />
          <div class="sign-card">
            <h3>{{ year }} 年 {{ month }} 月签到日历</h3>
            <p class="sign-count">本月已签到 <b>{{ signInfo.signCount }}</b> 天</p>
            <div class="calendar">
              <div class="week-head"><span v-for="w in ['日', '一', '二', '三', '四', '五', '六']" :key="w">{{ w }}</span></div>
              <div class="days">
                <div v-for="(c, i) in calendar" :key="i" class="day" :class="{ empty: c.day === null, signed: c.signed, today: c.today }">
                  {{ c.day ?? '' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="用户不存在">
      <el-button type="primary" @click="$router.push('/')">回首页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.user-page { max-width: 1080px; margin: 16px auto; }
.profile-card { background: #fff; border-radius: 6px; padding: 20px; display: flex; align-items: center; gap: 16px; }
.avatar { width: 72px; height: 72px; border-radius: 50%; background: #4e6ef2; color: #fff; font-size: 32px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.info { flex: 1; }
.info h2 { margin-bottom: 6px; }
.sign { color: #999; font-size: 13px; margin-bottom: 6px; }
.counts { color: #aaa; font-size: 12px; }
.actions { display: flex; gap: 8px; }
.body-grid { display: grid; grid-template-columns: 1fr 300px; gap: 16px; margin-top: 16px; }
.content-area { background: #fff; border-radius: 6px; padding: 0 18px 8px; }
.side-area { align-self: start; display: flex; flex-direction: column; gap: 16px; }
.side-card { margin: 0; }
.sign-card { background: #fff; border-radius: 6px; padding: 16px; }
.sign-card h3 { font-size: 14px; margin-bottom: 8px; }
.sign-count { font-size: 12px; color: #999; margin-bottom: 10px; }
.sign-count b { color: #4e6ef2; font-size: 16px; }
.calendar { font-size: 12px; }
.week-head { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; color: #999; margin-bottom: 4px; }
.days { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.day { height: 30px; display: flex; align-items: center; justify-content: center; border-radius: 4px; background: #f7f8fa; color: #666; }
.day.signed { background: #4e6ef2; color: #fff; }
.day.today { outline: 1px solid #4e6ef2; }
.day.empty { background: transparent; }
</style>
