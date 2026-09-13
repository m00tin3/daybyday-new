<script setup>
// 首页：帖子列表 + 热门吧/热议侧栏（视觉参考 tieba-demo）
import { ref } from 'vue'
import { getPostList } from '../api/post'
import { getBarRank } from '../api/bar'
import { getActivityList } from '../api/activity'
import PostCard from '../components/PostCard.vue'
import BadgePill from '../components/BadgePill.vue'

const loading = ref(true)
const posts = ref([])
const bars = ref([])
const page = ref(1)
const total = ref(0)
const size = 10

/**
 * 进行中的限量徽章（侧栏展示）。
 * 原实现把活动 ID 硬编码成 /activity/1001、/activity/1002，活动一旦重建/删除
 * 就会点进「活动不存在」；这里改为实时拉取，最多展示 3 个。
 */
const badges = ref([])

async function loadBadges() {
  try {
    const res = await getActivityList({ type: 2, page: 1, size: 20 })
    badges.value = (res.data?.list ?? []).filter(a => a.status === 1).slice(0, 3)
  } catch { badges.value = [] }
}

const barColors = ['#4e6ef2', '#f40', '#2db55d', '#ff8f1f']

/**
 * 加载失败状态。
 *
 * <p>原实现在接口失败时回退到一段硬编码的假帖子（「铲屎官小王」等），这里刻意
 * 不再兜底：假数据的字段名还与 PostCard 期望的对不上，渲染出来是 5 张空白卡片，
 * 用户既看不到真实内容、也意识不到后端已经挂了，比直接报错更糟。</p>
 */
const loadError = ref(false)

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const res = await getPostList({ page: page.value, size })
    posts.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    posts.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadBars() {
  try {
    const res = await getBarRank()
    bars.value = res.data ?? []
  } catch {
    bars.value = []
  }
}

function retry() {
  load()
  loadBars()
  loadBadges()
}

load()
loadBars()
loadBadges()
</script>

<template>
  <div class="container">
    <section class="post-list" v-loading="loading">
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
      <el-empty v-if="loadError" description="帖子加载失败，请稍后重试">
        <el-button type="primary" @click="retry">重新加载</el-button>
      </el-empty>
      <el-empty v-else-if="!loading && posts.length === 0" description="还没有帖子，快去发第一帖吧" />
    </section>

    <aside class="sidebar">
      <div class="side-card">
        <h3>热门吧</h3>
        <div v-for="(b, i) in bars" :key="b.id" class="side-item" @click="$router.push(`/bar/${b.id}`)">
          <div class="b-avatar" :style="{ background: barColors[i % 4] }">{{ b.name.slice(0, 1) }}</div>
          <a>{{ b.name }}</a>
          <span class="members">{{ b.memberCount }}</span>
        </div>
        <p v-if="!bars.length" class="side-empty">暂无贴吧</p>
      </div>
      <div class="side-card">
        <h3>发现</h3>
        <div class="side-item" @click="$router.push('/rank')"><a>🏆 排行榜</a></div>
        <div class="side-item" @click="$router.push('/search')"><a>🔥 热搜</a></div>
        <div class="side-item" @click="$router.push('/city')"><a>🏙 城市</a></div>
      </div>
      <div class="side-card">
        <h3>限量徽章</h3>
        <div v-for="a in badges" :key="a.id" class="side-item badge-item" @click="$router.push(`/activity/${a.id}`)">
          <BadgePill :name="a.badgeName" />
          <span class="badge-stock">剩 {{ a.remainStock }} / {{ a.stock }}</span>
        </div>
        <p v-if="!badges.length" class="side-empty">暂无进行中的徽章</p>
        <div class="side-item more" @click="$router.push('/activity')"><a>查看全部 →</a></div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.container { max-width: 1080px; margin: 16px auto; display: grid; grid-template-columns: 1fr 260px; gap: 16px; }
.post-list { background: #fff; border-radius: 6px; padding: 8px 0; min-height: 300px; }
.pager { padding: 12px 18px; justify-content: center; }
.sidebar { display: flex; flex-direction: column; gap: 16px; }
.side-card { background: #fff; border-radius: 6px; padding: 14px; }
.side-card h3 { font-size: 14px; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.side-item { display: flex; align-items: center; margin-bottom: 10px; font-size: 13px; cursor: pointer; }
.side-item .b-avatar { width: 30px; height: 30px; border-radius: 6px; margin-right: 10px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 14px; flex-shrink: 0; }
.side-item a { color: #333; text-decoration: none; }
.side-item:hover a { color: #4e6ef2; }
.side-item .members { margin-left: auto; color: #aaa; font-size: 12px; }
.side-item.badge-item { gap: 8px; }
.badge-stock { margin-left: auto; color: #aaa; font-size: 12px; }
.side-empty { font-size: 12px; color: #bbb; margin-bottom: 10px; }
.side-item.more a { color: #4e6ef2; font-size: 12px; }
</style>
