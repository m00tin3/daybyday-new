<script setup>
// 抢楼/限量徽章活动页（对应 API.md §3.6，秒杀接口已上线）
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityInfo, grabActivity } from '../api/activity'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activityId = route.params.id
const activity = ref(null)
const grabbing = ref(false)

const typeText = computed(() => (activity.value?.type === 1 ? '抢楼活动' : '限量徽章'))
const statusText = computed(() => ['未开始', '进行中', '已结束'][activity.value?.status] || '未知')
const statusTag = computed(() => (activity.value?.status === 1 ? 'success' : activity.value?.status === 2 ? 'info' : 'warning'))

async function load() {
  try {
    const res = await getActivityInfo(activityId)
    activity.value = res.data
  } catch {
    activity.value = null
  }
}

async function grab() {
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  grabbing.value = true
  try {
    const res = await grabActivity(activityId)
    ElMessage.success(res.msg || '抢楼成功')
    if (res.data?.floorNo) {
      ElMessage.success(`恭喜！你是第 ${res.data.floorNo} 楼`)
    }
    await load() // 刷新剩余库存与已抢状态
  } catch { /* 4001/4002/4003 业务码由拦截器提示 */ } finally {
    grabbing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="activity-page">
    <template v-if="activity">
      <div class="activity-card">
        <div class="badge">{{ typeText }}</div>
        <h2>{{ activity.title }}</h2>
        <p class="award">🎁 奖励：{{ activity.awardDesc || '无' }}</p>
        <p class="stock">剩余库存：<b>{{ activity.remainStock ?? activity.stock }}</b> / {{ activity.stock }}</p>
        <p class="status">
          状态：<el-tag :type="statusTag" size="small">{{ statusText }}</el-tag>
        </p>
        <p class="time">
          {{ activity.beginTime }} ~ {{ activity.endTime }}
        </p>
        <el-button
          type="danger"
          size="large"
          round
          class="grab-btn"
          :loading="grabbing"
          :disabled="activity.status !== 1 || activity.grabbed"
          @click="grab"
        >
          {{ activity.grabbed ? '已抢到' : activity.status === 1 ? '立即抢' : activity.status === 0 ? '未开始' : '已结束' }}
        </el-button>
      </div>
    </template>
    <el-empty v-else description="活动不存在或加载失败">
      <el-button type="primary" @click="$router.push('/')">回首页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.activity-page { max-width: 760px; margin: 24px auto; }
.activity-card { background: #fff; border-radius: 6px; padding: 32px; text-align: center; }
.badge { display: inline-block; background: #f40; color: #fff; border-radius: 3px; padding: 2px 10px; font-size: 12px; margin-bottom: 12px; }
.activity-card h2 { margin-bottom: 12px; }
.award { color: #666; margin-bottom: 8px; }
.stock { color: #999; font-size: 13px; margin-bottom: 8px; }
.stock b { color: #f40; font-size: 18px; }
.status { margin-bottom: 8px; }
.time { color: #bbb; font-size: 12px; margin-bottom: 16px; }
.grab-btn { min-width: 200px; }
</style>
