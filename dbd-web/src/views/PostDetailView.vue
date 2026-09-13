<script setup>
// 帖子详情：正文 + 点赞/收藏 + 楼层列表 + 回帖（对应 API.md §3.2.2/3.2.4-3.2.7）
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPostDetail, likePost, favoritePost, getComments, addComment } from '../api/post'
import { useUserStore } from '../stores/user'
import BadgePill from '../components/BadgePill.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = route.params.id
const post = ref(null)
const loading = ref(true)
const comments = ref([])
const commentTotal = ref(0)
const page = ref(1)
const size = ref(10)
const commentText = ref('')
const submitting = ref(false)

async function loadPost() {
  try {
    const res = await getPostDetail(postId)
    post.value = res.data
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  try {
    const res = await getComments(postId, { page: page.value, size: size.value })
    comments.value = res.data?.list ?? []
    commentTotal.value = res.data?.total ?? 0
  } catch {
    comments.value = []
  }
}

// 点赞/收藏：幂等切换，接口返回最新状态
async function toggleLike() {
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const res = await likePost(postId)
    post.value.isLiked = res.data.isLiked
    post.value.likeCount = res.data.likeCount
  } catch { /* 拦截器已提示 */ }
}

async function toggleFavorite() {
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const res = await favoritePost(postId)
    post.value.isFavorited = res.data.isFavorited
    post.value.favoriteCount = res.data.favoriteCount
  } catch { /* 拦截器已提示 */ }
}

// 回帖：成功后刷新楼层并清空输入
async function submitComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('先说点什么吧')
    return
  }
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  submitting.value = true
  try {
    const res = await addComment(postId, { content: commentText.value.trim() })
    ElMessage.success(`盖楼成功，你是第 ${res.data.floorNo} 楼`)
    commentText.value = ''
    post.value.commentCount = (Number(post.value.commentCount) || 0) + 1
    await loadComments()
  } catch { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadPost()
  loadComments()
})
</script>

<template>
  <div class="detail-container">
    <!-- 帖子正文 -->
    <div v-loading="loading" class="post-body">
      <template v-if="post">
        <div class="post-head">
          <div class="avatar">{{ post.author?.icon || '👤' }}</div>
          <div class="head-meta">
            <div class="title">
              <span v-if="post.isTop" class="tag">顶</span>
              <span v-else-if="post.status === 2" class="tag jing">精</span>
              {{ post.title }}
            </div>
            <div class="sub">
              <span class="user" @click="$router.push(`/user/${post.author?.id}`)">{{ post.author?.nickname }}</span>
              <BadgePill v-for="b in post.author?.badges || []" :key="b" :name="b" class="inline-badge" />
              ·
              <span class="bar" @click="$router.push(`/bar/${post.barId}`)">{{ post.barName }}</span>
              <template v-if="post.city">
                ·
                <span class="city" @click="$router.push('/city')">🏙 {{ post.city }}</span>
              </template>
              · {{ post.createdAt }}
              · 浏览 {{ post.viewCount }} · UV {{ post.uvCount }}
            </div>
          </div>
        </div>
        <div class="content">{{ post.content }}</div>
        <div v-if="post.images?.length" class="images">
          <img v-for="img in post.images" :key="img" :src="img" alt="帖子图片" />
        </div>
        <div class="actions">
          <el-button :type="post.isLiked ? 'danger' : 'default'" round @click="toggleLike">
            👍 点赞 {{ post.likeCount }}
          </el-button>
          <el-button :type="post.isFavorited ? 'warning' : 'default'" round @click="toggleFavorite">
            ⭐ 收藏 {{ post.favoriteCount }}
          </el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="帖子不存在或已删除">
        <el-button type="primary" @click="$router.push('/')">回首页</el-button>
      </el-empty>
    </div>

    <!-- 楼层列表 -->
    <div class="comments" v-if="post">
      <h3 class="section-title">全部回复（{{ post.commentCount }}）</h3>
      <div v-for="c in comments" :key="c.id" class="floor">
        <div class="floor-avatar">{{ c.author?.icon || '👤' }}</div>
        <div class="floor-main">
          <div class="floor-head">
            <span class="floor-no">{{ c.floorNo }}楼</span>
            <span class="user" @click="$router.push(`/user/${c.author?.id}`)">{{ c.author?.nickname }}</span>
            <BadgePill v-for="b in c.author?.badges || []" :key="b" :name="b" class="inline-badge" />
            <span class="time">{{ c.createdAt }}</span>
          </div>
          <div class="floor-content">{{ c.content }}</div>
        </div>
      </div>
      <el-pagination
        v-if="commentTotal > size"
        class="pager"
        background
        layout="prev, pager, next"
        :total="commentTotal"
        :page-size="size"
        :current-page="page"
        @current-change="(p) => { page = p; loadComments() }"
      />
      <el-empty v-if="comments.length === 0" description="还没有回复，来抢沙发" />

      <!-- 回帖 -->
      <div class="reply-box">
        <el-input
          v-model="commentText"
          type="textarea"
          :rows="3"
          maxlength="2048"
          show-word-limit
          placeholder="文明回帖，一起盖楼～"
        />
        <div class="reply-actions">
          <el-button type="primary" :loading="submitting" @click="submitComment">回帖</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-container { max-width: 1080px; margin: 16px auto; }
.post-body { background: #fff; border-radius: 6px; padding: 20px; min-height: 200px; }
.post-head { display: flex; margin-bottom: 16px; }
.avatar { width: 48px; height: 48px; border-radius: 50%; background: #4e6ef2; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; margin-right: 14px; flex-shrink: 0; }
.head-meta { flex: 1; }
.title { font-size: 18px; font-weight: bold; margin-bottom: 6px; }
.title .tag { font-size: 12px; color: #fff; background: #f40; border-radius: 3px; padding: 1px 5px; margin-right: 6px; }
.title .tag.jing { background: #2db55d; }
.sub { font-size: 12px; color: #999; }
.sub .user { color: #4e6ef2; cursor: pointer; }
.sub .bar { cursor: pointer; }
.sub .bar:hover { color: #4e6ef2; }
.sub .city { cursor: pointer; }
.sub .city:hover { color: #4e6ef2; }
.content { font-size: 15px; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.images { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.images img { max-width: 200px; max-height: 200px; border-radius: 6px; }
.actions { margin-top: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0; }

.comments { background: #fff; border-radius: 6px; padding: 16px 20px; margin-top: 16px; }
.section-title { font-size: 14px; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.floor { display: flex; padding: 12px 0; border-bottom: 1px solid #f0f0f0; }
.floor-avatar { width: 36px; height: 36px; border-radius: 50%; background: #8ba3f5; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 16px; margin-right: 12px; flex-shrink: 0; }
.floor-main { flex: 1; min-width: 0; }
.floor-head { font-size: 12px; margin-bottom: 6px; }
.floor-no { color: #4e6ef2; font-weight: bold; margin-right: 8px; }
.floor-head .user { color: #4e6ef2; cursor: pointer; }
.floor-head .time { color: #bbb; margin-left: 8px; }
/* 作者昵称旁的限量徽章角标（帖子正文头部与各楼层共用） */
.inline-badge { margin-left: 5px; }
.floor-content { font-size: 14px; line-height: 1.6; word-break: break-word; }
.pager { padding: 12px 0; justify-content: center; }
.reply-box { margin-top: 16px; }
.reply-actions { text-align: right; margin-top: 8px; }
</style>
