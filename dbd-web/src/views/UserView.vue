<script setup>
// 个人中心：主页信息 + 关注按钮 + 签到日历 + 帖子/回复/收藏 Tab（对应 API.md §3.4）
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserProfile, getUserPosts, getUserFavorites, getUserReplies, getSignCalendar, followUser } from '../api/user'
import { deleteOwnPost, deleteOwnComment } from '../api/post'
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

// 「TA 的回复」——与另外两个 Tab 不同，这个带分页（历史发言可能很长）
const replies = ref([])
const replyPage = ref(1)
const replyTotal = ref(0)
const replySize = 20

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

async function loadReplies() {
  try {
    const res = await getUserReplies(userId, { page: replyPage.value, size: replySize })
    replies.value = res.data?.list ?? []
    replyTotal.value = res.data?.total ?? 0
  } catch {
    replies.value = []
    replyTotal.value = 0
  }
}

function changeReplyPage(p) {
  replyPage.value = p
  loadReplies()
}

/* ==================== 删除自己的内容 ==================== */

/**
 * 删除确认框。
 * 产品要求"对用户伪装成真删除"，所以措辞按不可恢复写，也不提任何恢复途径。
 */
async function confirmDelete(text) {
  try {
    await ElMessageBox.confirm(text, '确认删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    return true
  } catch {
    return false // 用户取消
  }
}

async function onDeletePost(p) {
  if (!(await confirmDelete(`确定删除帖子「${p.title}」？删除后不可恢复。`))) return
  try {
    await deleteOwnPost(p.id)
    ElMessage.success('已删除')
    // 本地摘掉，不整表重拉（与列表分页状态无关）
    posts.value = posts.value.filter(x => x.id !== p.id)
    if (profile.value?.postCount > 0) profile.value.postCount--
  } catch { /* 拦截器已提示 */ }
}

async function onDeleteReply(r) {
  if (!(await confirmDelete('确定删除这条回复？删除后不可恢复。'))) return
  try {
    await deleteOwnComment(r.postId, r.id)
    ElMessage.success('已删除')
    replies.value = replies.value.filter(x => x.id !== r.id)
    if (replyTotal.value > 0) replyTotal.value--
    if (profile.value?.replyCount > 0) profile.value.replyCount--
  } catch { /* 拦截器已提示 */ }
}

/**
 * 点「TA 的回复」里的一条。
 * 所属帖子已被删除时只弹提示、**不跳转** —— 跳过去只会看到 404 空页。
 */
function openReplyTarget(r) {
  if (r.postDeleted) {
    ElMessage.warning('此帖已被删除')
    return
  }
  router.push(`/post/${r.postId}`)
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
  if (t === 'replies' && replies.value.length === 0) loadReplies()
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
            发帖 {{ profile.postCount }} · 回复 {{ profile.replyCount }} · 粉丝 {{ profile.followerCount }} · 关注 {{ profile.followingCount }}
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
              <PostCard v-for="p in posts" :key="p.id" :post="p">
                <!-- 只有自己的主页才给删除入口 -->
                <template v-if="isSelf" #actions>
                  <el-button link type="danger" size="small" @click="onDeletePost(p)">删除</el-button>
                </template>
              </PostCard>
              <el-empty v-if="posts.length === 0" description="还没有发过帖" />
            </el-tab-pane>

            <!-- 回复列表所有人可见；删除按钮只在自己的主页出现 -->
            <el-tab-pane label="TA 的回复" name="replies">
              <div v-for="r in replies" :key="r.id" class="reply-item" @click="openReplyTarget(r)">
                <div class="reply-head">
                  <span class="reply-post" :class="{ gone: r.postDeleted }">
                    {{ r.postDeleted ? '该帖已被删除' : r.postTitle }}
                  </span>
                  <span class="reply-tag">{{ r.nested ? '楼中楼' : `${r.floorNo}楼` }}</span>
                  <span class="reply-time">{{ r.createdAt }}</span>
                </div>
                <div class="reply-body">{{ r.content }}</div>
                <el-button
                  v-if="isSelf"
                  link
                  type="danger"
                  size="small"
                  class="reply-del"
                  @click.stop="onDeleteReply(r)"
                >删除</el-button>
              </div>
              <el-empty v-if="replies.length === 0" description="还没有回复过" />
              <el-pagination
                v-if="replyTotal > replySize"
                class="pager"
                background
                layout="total, prev, pager, next"
                :total="replyTotal"
                :current-page="replyPage"
                :page-size="replySize"
                @current-change="changeReplyPage"
              />
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

/* ==================== 「TA 的回复」列表 ==================== */
.reply-item { position: relative; padding: 12px 0; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.reply-item:hover { background: #f7f9ff; }
.reply-head { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #999; margin-bottom: 6px; }
.reply-post { color: #4e6ef2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 60%; }
/* 所属帖子已被删除：灰斜体，和正常标题区分开 */
.reply-post.gone { color: #bbb; font-style: italic; cursor: default; }
.reply-tag { background: #f0f2f5; color: #666; border-radius: 3px; padding: 1px 5px; flex-shrink: 0; }
.reply-time { margin-left: auto; color: #bbb; flex-shrink: 0; }
.reply-body { font-size: 14px; line-height: 1.6; color: #333; word-break: break-word; padding-right: 52px; }
.reply-del { position: absolute; right: 0; bottom: 10px; }
.pager { padding: 12px 0; justify-content: center; }
</style>
