<script setup>
// 抢楼/限量徽章活动页（对应 API.md §3.6，后端属于阶段三）
// 当前以降级 UI 展示活动结构，秒杀接口就绪后自动生效
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
const available = ref(true)
const grabbing = ref(false)

// 演示数据：后端未实现时展示活动结构（与种子数据 1001/1002 对应）
const demoActivities = {
  1001: { id: 1001, title: '猫咪吧 3 周年限量徽章', type: 2, stock: 100, remainStock: 100, awardDesc: '「猫奴认证」专属徽章', status: 1, grabbed: false },
  1002: { id: 1002, title: '新版本攻略抢楼活动', type: 1, stock: 300, remainStock: 300, awardDesc: '抢到 8 楼/88 楼/888 楼送皮肤', status: 1, grabbed: false }
}

const typeText = computed(() => (activity.value?.type === 1 ? '抢楼活动' : '限量徽章'))
const statusText = computed(() => ['未开始', '进行中', '已结束'][activity.value?.status] || '未知')

async function load() {
  try {
    const res = await getActivityInfo(activityId)
    activity.value = res.data
    available.value = true
  } catch {
    activity.value = demoActivities[activityId]
    available.value = false
  }
}

async function grab() {
  if (!userStore.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!available.value) {
    ElMessage.warning('秒杀接口（阶段三 Lua 预扣）开发中，敬请期待')
    return
  }
  grabbing.value = true
  try {
    const res = await grabActivity(activityId)
    ElMessage.success(res.msg || '抢楼成功')
    activity.value.grabbed = true
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
          状态：<el-tag :type="activity.status === 1 ? 'success' : 'info'" size="small">{{ statusText }}</el-tag>
        </p>
        <el-alert
          v-if="!available"
          type="warning"
          :closable="false"
          show-icon
          title="当前为演示数据 · 秒杀接口（Lua 预扣库存 + 一人一单）开发中，阶段三上线"
          class="dev-alert"
        />
        <el-button
          type="danger"
          size="large"
          round
          class="grab-btn"
          :loading="grabbing"
          :disabled="activity.status !== 1 || activity.grabbed"
          @click="grab"
        >
          {{ activity.grabbed ? '已抢到' : activity.status === 1 ? '立即抢' : '未开始/已结束' }}
        </el-button>
      </div>
    </template>
    <el-empty v-else description="活动不存在">
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
.status { margin-bottom: 16px; }
.dev-alert { margin: 12px auto 16px; max-width: 480px; text-align: left; }
.grab-btn { min-width: 200px; }
</style>
