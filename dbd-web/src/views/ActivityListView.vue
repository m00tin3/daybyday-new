<script setup>
// 活动广场：不限量徽章抢夺活动列表（对应 GET /api/activity）
// 剩余库存与「是否已抢」都由后端实时给出（Redis），前端只负责展示与跳转。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getActivityList } from '../api/activity'
import { useUserStore } from '../stores/user'
import BadgePill from '../components/BadgePill.vue'

const router = useRouter()
const userStore = useUserStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)

// 状态筛选在前端做：活动总量本就不大（一场活动一个徽章），
// 且后端返回的是按时间算出的动态状态，前端再分页反而容易错位。
const filter = ref('all')

const STATUS_TEXT = ['未开始', '进行中', '已结束']
const STATUS_TYPE = ['warning', 'success', 'info']

const filtered = computed(() => {
  if (filter.value === 'all') return list.value
  const want = filter.value === 'ongoing' ? 1 : filter.value === 'upcoming' ? 0 : 2
  return list.value.filter(a => a.status === want)
})

const counts = computed(() => ({
  all: list.value.length,
  ongoing: list.value.filter(a => a.status === 1).length,
  upcoming: list.value.filter(a => a.status === 0).length,
  ended: list.value.filter(a => a.status === 2).length
}))

async function load() {
  loading.value = true
  try {
    const res = await getActivityList({ type: 2, page: page.value, size })
    list.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openCard(a) {
  router.push(`/activity/${a.id}`)
}

/** 已抢百分比，用于进度条 */
function percent(a) {
  if (!a.stock) return 0
  return Math.min(100, Math.round((a.awardedCount || 0) * 100 / a.stock))
}

function buttonText(a) {
  if (a.grabbed) return '已获得'
  if (a.status === 0) return '未开始'
  if (a.status === 2) return '已结束'
  return a.remainStock > 0 ? '立即抢' : '已抢光'
}

onMounted(load)
</script>

<template>
  <div class="plaza-page" v-loading="loading">
    <div class="plaza-head">
      <div>
        <h2>限量徽章抢夺</h2>
        <p class="sub">
          每个称号全站限量发放，抢到即永久挂在你的昵称旁。一人每个徽章限领 1 枚。
        </p>
      </div>
      <div v-if="!userStore.token" class="login-hint">
        <el-button type="primary" round @click="$router.push({ name: 'login', query: { redirect: '/activity' } })">
          登录后参与
        </el-button>
      </div>
    </div>

    <el-radio-group v-model="filter" class="filter-bar">
      <el-radio-button value="all">全部 ({{ counts.all }})</el-radio-button>
      <el-radio-button value="ongoing">进行中 ({{ counts.ongoing }})</el-radio-button>
      <el-radio-button value="upcoming">未开始 ({{ counts.upcoming }})</el-radio-button>
      <el-radio-button value="ended">已结束 ({{ counts.ended }})</el-radio-button>
    </el-radio-group>

    <div v-if="filtered.length" class="card-grid">
      <div
        v-for="a in filtered"
        :key="a.id"
        class="badge-card"
        :class="{ ended: a.status === 2, grabbed: a.grabbed }"
        @click="openCard(a)"
      >
        <div class="card-top">
          <BadgePill :name="a.badgeName || a.title" size="large" />
          <el-tag :type="STATUS_TYPE[a.status]" size="small" effect="dark">{{ STATUS_TEXT[a.status] }}</el-tag>
        </div>

        <h3 class="card-title">{{ a.title }}</h3>
        <p class="card-award">{{ a.awardDesc || '限量发放，先到先得' }}</p>

        <div class="stock-line">
          <span>已抢 <b>{{ a.awardedCount || 0 }}</b> / {{ a.stock }}</span>
          <span class="remain">剩余 <b>{{ a.remainStock ?? a.stock }}</b></span>
        </div>
        <el-progress :percentage="percent(a)" :show-text="false" :stroke-width="6"
                     :color="a.status === 2 ? '#c0c4cc' : '#f56c6c'" />

        <p class="card-time">{{ a.beginTime }} ~ {{ a.endTime }}</p>

        <el-button
          class="card-btn"
          :type="a.grabbed ? 'success' : a.status === 1 && a.remainStock > 0 ? 'danger' : 'info'"
          :disabled="a.status !== 1 || a.grabbed || a.remainStock <= 0"
          round
          @click.stop="openCard(a)"
        >
          {{ buttonText(a) }}
        </el-button>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="暂时没有徽章活动">
      <el-button type="primary" @click="$router.push('/')">回首页</el-button>
    </el-empty>

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
</template>

<style scoped>
.plaza-page { max-width: 1080px; margin: 16px auto; }
.plaza-head { background: #fff; border-radius: 6px; padding: 18px 22px; margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.plaza-head h2 { font-size: 18px; margin-bottom: 6px; }
.sub { font-size: 12px; color: #999; }
.login-hint { flex-shrink: 0; }
.filter-bar { margin-bottom: 14px; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.badge-card { background: #fff; border-radius: 8px; padding: 16px 18px; cursor: pointer; border: 1px solid #eef0f5; transition: box-shadow .15s, transform .15s; }
.badge-card:hover { box-shadow: 0 6px 18px rgba(78, 110, 242, .14); transform: translateY(-2px); }
.badge-card.ended { opacity: .68; }
.badge-card.grabbed { border-color: #67c23a; }
.card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.card-title { font-size: 15px; margin-bottom: 6px; }
.card-award { font-size: 12px; color: #888; margin-bottom: 12px; min-height: 18px; }
.stock-line { display: flex; justify-content: space-between; font-size: 12px; color: #999; margin-bottom: 6px; }
.stock-line b { color: #f56c6c; font-size: 14px; }
.stock-line .remain b { color: #4e6ef2; }
.card-time { font-size: 11px; color: #bbb; margin: 10px 0 12px; }
.card-btn { width: 100%; }
.pager { margin-top: 16px; justify-content: center; }
</style>
