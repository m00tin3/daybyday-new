<script setup>
// 帖子详情：正文 + 点赞/收藏 + 楼层列表 + 回帖（对应 API.md §3.2.2/3.2.4-3.2.7）
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPostDetail, likePost, favoritePost, getComments, getFloorReplies, addComment,
  deleteOwnPost, deleteOwnComment
} from '../api/post'
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

/** 每层楼内联展示的子回复条数；超过就层内分页（与后端 PostServiceImpl.REPLY_PAGE_SIZE 保持一致） */
const REPLY_PAGE_SIZE = 10

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
    // 楼层数据整体刷新了，各层的翻页缓存必须一起作废（否则会停在旧的第 N 页）
    replyPages.value = {}
  } catch {
    comments.value = []
  }
}

/* ==================== 楼中楼翻页（仿贴吧的层内翻页） ==================== */

// 每层楼的翻页状态：{ [floorId]: { page, list, total, loading } }。
// 第 1 页直接用 comments() 随楼层带回来的 c.replies，翻到第 2 页起才请求并覆盖。
const replyPages = ref({})

/** 该层当前要显示的回复：翻过页就用翻页缓存，否则用楼层自带的第 1 页 */
function repliesOf(c) {
  return replyPages.value[c.id]?.list ?? c.replies ?? []
}

function replyPageOf(c) {
  return replyPages.value[c.id]?.page ?? 1
}

function totalReplyPages(c) {
  return Math.max(1, Math.ceil((c.replyCount || 0) / REPLY_PAGE_SIZE))
}

async function changeReplyPage(c, p) {
  if (p < 1 || p > totalReplyPages(c) || replyPages.value[c.id]?.loading) {
    return
  }
  replyPages.value[c.id] = { ...(replyPages.value[c.id] || {}), loading: true }
  try {
    const res = await getFloorReplies(postId, c.id, { page: p, size: REPLY_PAGE_SIZE })
    replyPages.value[c.id] = {
      page: p,
      list: res.data?.list ?? [],
      total: res.data?.total ?? 0,
      loading: false
    }
  } catch {
    replyPages.value[c.id] = { ...(replyPages.value[c.id] || {}), loading: false }
  }
}

// 未登录时的统一去向：带上当前路径，登录后回跳本页
function goLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

// 点赞/收藏：幂等切换，接口返回最新状态
async function toggleLike() {
  if (!userStore.token) {
    goLogin()
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
    goLogin()
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
    goLogin()
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

/* ==================== 楼中楼（固定 2 层） ==================== */

// 哪一层楼展开了行内输入框（存顶层楼层的 id）
const replyToId = ref('')
// 正在回复的那条评论：点楼层的「回复」时为 null，点子回复的「回复」时是那条子回复
const replyTarget = ref(null)
const replyText = ref('')
const replySubmitting = ref(false)

/**
 * 打开某一层的行内回复框。
 * @param floor  该顶层楼层（结构的 parentId 恒为它）
 * @param target 被点的子回复；不传表示回复楼层本身
 */
function openReply(floor, target) {
  if (!userStore.token) {
    goLogin()
    return
  }
  replyToId.value = floor.id
  replyTarget.value = target || null
  replyText.value = ''
}

function closeReply() {
  replyToId.value = ''
  replyTarget.value = null
  replyText.value = ''
}

async function submitReply(floor) {
  if (!replyText.value.trim()) {
    ElMessage.warning('先说点什么吧')
    return
  }
  if (!userStore.token) {
    goLogin()
    return
  }
  replySubmitting.value = true
  try {
    await addComment(postId, {
      content: replyText.value.trim(),
      // 结构上的父楼层恒为这一层（两层结构不允许三层）
      parentId: floor.id,
      // 被回复的那条评论：后端据此把通知发给正确的人，而不是一律发给楼主
      replyToCommentId: replyTarget.value?.id
    })
    ElMessage.success('回复成功')
    closeReply()
    post.value.commentCount = (Number(post.value.commentCount) || 0) + 1
    await loadComments()
  } catch { /* 拦截器已提示 */ } finally {
    replySubmitting.value = false
  }
}

/* ==================== 删除自己的内容 ==================== */

/** 是否是我发的。ID 由后端 ToStringSerializer 下发，两侧都是字符串，直接比即可 */
function isMine(author) {
  return !!userStore.userInfo && userStore.userInfo.id === author?.id
}

/** 删除确认框。产品要求"对用户伪装成真删除"，措辞按不可恢复写，也不提任何恢复途径 */
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

async function onDeletePost() {
  if (!(await confirmDelete(`确定删除帖子「${post.value.title}」？删除后不可恢复。`))) return
  try {
    await deleteOwnPost(postId)
    ElMessage.success('已删除')
    router.push('/')
  } catch { /* 拦截器已提示 */ }
}

/**
 * 删自己的楼层或子回复。
 * @param comment 要删的那条
 */
async function onDeleteComment(comment) {
  if (!(await confirmDelete('确定删除这条回复？删除后不可恢复。'))) return
  try {
    await deleteOwnComment(postId, comment.id)
    ElMessage.success('已删除')
    // 头部「全部回复（N）」要跟着减，否则同一页连删两条数字不动
    post.value.commentCount = Math.max(0, (Number(post.value.commentCount) || 0) - 1)
    // loadComments 会整体刷新楼层并清空各层的翻页缓存
    await loadComments()
  } catch { /* 拦截器已提示 */ }
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
              <!-- 公告的 is_top 恒为 1，必须排在「顶」之前判断 -->
              <span v-if="post.type === 1" class="tag notice">公告</span>
              <span v-else-if="post.isTop" class="tag">顶</span>
              <span v-else-if="post.status === 2" class="tag jing">精</span>
              {{ post.title }}
            </div>
            <div class="sub">
              <span class="user" @click="$router.push(`/user/${post.author?.id}`)">{{ post.author?.nickname }}</span>
              <BadgePill v-for="b in post.author?.badges || []" :key="b" :name="b" class="inline-badge" />
              <!-- 公告没有所属吧，barName 为 null：这里必须加 v-if，
                   否则会渲染出一个空白但可点击的 span，点进去是 /bar/null 报「吧不存在」 -->
              <template v-if="post.barName">
                ·
                <span class="bar" @click="$router.push(`/bar/${post.barId}`)">{{ post.barName }}</span>
              </template>
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
          <!-- 只有自己发的帖子才给删除入口 -->
          <el-button v-if="isMine(post.author)" round type="danger" plain @click="onDeletePost">删除</el-button>
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
        <div class="floor-avatar">{{ c.deleted ? '' : (c.author?.icon || '👤') }}</div>
        <div class="floor-main">
          <!-- 作者已删除的楼层：只留占位。内容与作者后端已经不给（脱敏），
               但**下面的楼中楼照常渲染** —— 需求要求删楼层不隐藏别人的回复。 -->
          <template v-if="c.deleted">
            <div class="floor-head">
              <span class="floor-no">{{ c.floorNo }}楼</span>
              <span class="floor-gone">该楼层已被删除</span>
            </div>
          </template>
          <template v-else>
            <div class="floor-head">
              <span class="floor-no">{{ c.floorNo }}楼</span>
              <span class="user" @click="$router.push(`/user/${c.author?.id}`)">{{ c.author?.nickname }}</span>
              <BadgePill v-for="b in c.author?.badges || []" :key="b" :name="b" class="inline-badge" />
              <span class="time">{{ c.createdAt }}</span>
              <span class="reply-btn" @click="openReply(c)">回复</span>
              <span v-if="isMine(c.author)" class="reply-btn del" @click="onDeleteComment(c)">删除</span>
            </div>
            <div class="floor-content">{{ c.content }}</div>
          </template>

          <!-- 楼中楼：第 1 页内联完整展示；超过一页时在该层内翻页（仿贴吧） -->
          <div v-if="c.replyCount > 0" class="sub-list" v-loading="replyPages[c.id]?.loading">
            <div v-for="r in repliesOf(c)" :key="r.id" class="sub-item">
              <span class="sub-user" @click="$router.push(`/user/${r.author?.id}`)">{{ r.author?.nickname }}</span>
              <BadgePill v-for="b in r.author?.badges || []" :key="b" :name="b" class="inline-badge" />
              <!-- 只有"回复的不是楼主层本身"时才显示 @，避免满屏「回复 @楼主」 -->
              <span v-if="r.replyToNickname && r.replyToUserId !== c.author?.id" class="sub-at">
                回复 <span class="sub-at-name">@{{ r.replyToNickname }}</span>
              </span>
              <span class="sub-content">{{ r.content }}</span>
              <span class="sub-time">{{ r.createdAt }}</span>
              <!-- 父楼层已被删除时整棵子树冻结：后端也会拒绝（不能往已删楼层下挂新回复） -->
              <span v-if="!c.deleted" class="reply-btn" @click="openReply(c, r)">回复</span>
              <span v-if="isMine(r.author)" class="reply-btn del" @click="onDeleteComment(r, c)">删除</span>
            </div>

            <div v-if="c.replyCount > REPLY_PAGE_SIZE" class="sub-pager">
              <span class="sub-total">共 {{ c.replyCount }} 条回复</span>
              <el-button link size="small" :disabled="replyPageOf(c) <= 1"
                         @click="changeReplyPage(c, replyPageOf(c) - 1)">上一页</el-button>
              <span class="sub-page-no">{{ replyPageOf(c) }}/{{ totalReplyPages(c) }}</span>
              <el-button link size="small" :disabled="replyPageOf(c) >= totalReplyPages(c)"
                         @click="changeReplyPage(c, replyPageOf(c) + 1)">下一页</el-button>
            </div>
          </div>

          <!-- 行内回复框：同一时刻只展开一层；已删楼层不提供（后端也会拒绝） -->
          <div v-if="replyToId === c.id && !c.deleted" class="sub-reply-box">
            <el-input
              v-model="replyText"
              type="textarea"
              :rows="2"
              maxlength="2048"
              :placeholder="replyTarget ? `回复 @${replyTarget.author?.nickname}` : `回复 ${c.author?.nickname}`"
            />
            <div class="sub-reply-actions">
              <el-button size="small" @click="closeReply">取消</el-button>
              <el-button size="small" type="primary" :loading="replySubmitting" @click="submitReply(c)">
                回复
              </el-button>
            </div>
          </div>
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

      <!-- 回帖：未登录时整块禁用，点击跳登录页 -->
      <div class="reply-box">
        <el-input
          v-model="commentText"
          type="textarea"
          :rows="3"
          maxlength="2048"
          show-word-limit
          :disabled="!userStore.token"
          :placeholder="userStore.token ? '文明回帖，一起盖楼～' : '非登录用户无法点击和输入'"
        />
        <div class="reply-actions">
          <el-button type="primary" :disabled="!userStore.token" :loading="submitting" @click="submitComment">
            回帖
          </el-button>
        </div>
        <!-- 禁用态的 el-input 不派发 click 事件，所以用一层透明遮罩承接点击 -->
        <div v-if="!userStore.token" class="reply-mask" @click="goLogin"></div>
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
/* 公告：官方口径，用深红与「顶」的橙红区分开 */
.title .tag.notice { background: #b91c1c; }
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
.floor-head .reply-btn { color: #bbb; margin-left: 12px; cursor: pointer; }
.floor-head .reply-btn:hover { color: #4e6ef2; }
/* 删除入口：比「回复」更靠右、颜色也提示危险性 */
.reply-btn.del { color: #f56c6c; }
.reply-btn.del:hover { color: #c45656; }
/* 已删除楼层的占位文案 */
.floor-gone { color: #bbb; font-style: italic; }

/* ==================== 楼中楼 ==================== */
.sub-list { margin-top: 8px; padding: 6px 10px; background: #f7f8fa; border-radius: 4px; position: relative; }
.sub-item { font-size: 13px; line-height: 1.7; padding: 3px 0; border-bottom: 1px dashed #ececec; }
.sub-item:last-child { border-bottom: none; }
.sub-user { color: #4e6ef2; cursor: pointer; }
.sub-at { color: #999; }
.sub-at-name { color: #4e6ef2; }
.sub-content { color: #333; margin-left: 4px; word-break: break-word; }
.sub-time { color: #bbb; font-size: 12px; margin-left: 8px; }
.sub-item .reply-btn { color: #bbb; font-size: 12px; margin-left: 8px; cursor: pointer; }
.sub-item .reply-btn:hover { color: #4e6ef2; }
/* 层内翻页条（仿贴吧）：一页放不下时才出现 */
.sub-pager { display: flex; align-items: center; gap: 8px; padding-top: 6px; margin-top: 2px; border-top: 1px dashed #e4e4e4; font-size: 12px; }
.sub-pager .sub-total { color: #4e6ef2; }
.sub-pager .sub-page-no { color: #999; }
.sub-reply-box { margin-top: 10px; }
.sub-reply-actions { text-align: right; margin-top: 6px; }
.pager { padding: 12px 0; justify-content: center; }
.reply-box { margin-top: 16px; position: relative; }
.reply-actions { text-align: right; margin-top: 8px; }
/* 未登录遮罩：禁用态的 el-input 不派发 click，只能靠这层透明层承接「点击跳登录」 */
.reply-mask { position: absolute; inset: 0; cursor: pointer; }
</style>
