<script setup>
// 消息通知页（对应 API.md §3.10）
//
// 「打开列表即自动已读」的实现方式：进入页面先 load() 渲染出未读样式，
// 再显式调一次 read-all。刻意不让 GET 列表接口顺手改已读状态 ——
// GET 带副作用会污染缓存语义，也让接口没法被安全地重复调用。
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, readAllNotifications } from '../api/notification'
import { useNoticeStore } from '../stores/notice'
import BadgePill from '../components/BadgePill.vue'

const router = useRouter()
const noticeStore = useNoticeStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getNotifications({ page: page.value, size: size.value })
    list.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 全部标为已读，并把顶栏红点清掉 */
async function markAllRead() {
  try {
    await readAllNotifications()
    noticeStore.clear()
  } catch { /* 拦截器已提示 */ }
}

function open(n) {
  // 帖子被物理删除时 postId 仍在，但详情会 404 —— 后端约定此处不保证帖子还在
  if (n.postId) router.push(`/post/${n.postId}`)
}

onMounted(async () => {
  await load()
  await markAllRead()
})
</script>

<template>
  <div class="notice-page">
    <div class="card" v-loading="loading">
      <h3 class="section-title">消息</h3>

      <div v-for="n in list" :key="n.id" class="notice-item" :class="{ unread: !n.isRead }" @click="open(n)">
        <div class="avatar">{{ n.fromUser?.icon || '👤' }}</div>
        <div class="main">
          <div class="head">
            <span class="user" @click.stop="$router.push(`/user/${n.fromUser?.id}`)">{{ n.fromUser?.nickname }}</span>
            <BadgePill v-for="b in n.fromUser?.badges || []" :key="b" :name="b" class="inline-badge" @click.stop />
            <span class="action">{{ n.typeText }}</span>
            <span class="dot" v-if="!n.isRead"></span>
            <span class="time">{{ n.createdAt }}</span>
          </div>
          <!-- 判断"帖子没了"必须用 postDeleted 而不是 postTitle 是否为空：
               帖子被作者软删后行还在、标题照样查得到 -->
          <div class="post-title" :class="{ gone: n.postDeleted }">
            {{ n.postDeleted ? '帖子已删除' : n.postTitle }}
          </div>
          <div class="snippet" v-if="n.contentSnippet">{{ n.contentSnippet }}</div>
        </div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="还没有新消息" />

      <el-pagination
        v-if="total > size"
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="(p) => { page = p; load() }"
      />
    </div>
  </div>
</template>

<style scoped>
.notice-page { max-width: 820px; margin: 16px auto; }
.card { background: #fff; border-radius: 6px; padding: 16px 20px; }
.section-title { font-size: 14px; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.notice-item { display: flex; padding: 12px 0; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.notice-item:hover { background: #f7f9ff; }
.avatar { width: 40px; height: 40px; border-radius: 50%; background: #8ba3f5; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; margin-right: 12px; flex-shrink: 0; }
.main { flex: 1; min-width: 0; }
.head { font-size: 12px; margin-bottom: 4px; display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.head .user { color: #4e6ef2; cursor: pointer; }
.head .action { color: #666; }
.head .time { color: #bbb; margin-left: auto; }
.head .dot { width: 6px; height: 6px; border-radius: 50%; background: #f40; display: inline-block; }
.inline-badge { margin-left: 0; }
.post-title { font-size: 14px; color: #333; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.post-title.gone { color: #bbb; font-style: italic; }
.snippet { font-size: 13px; color: #999; line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pager { padding: 12px 0; justify-content: center; }
/* 未读整条加一层淡底，读完之后自动恢复正常 */
.notice-item.unread { background: #fbfcff; }
</style>
