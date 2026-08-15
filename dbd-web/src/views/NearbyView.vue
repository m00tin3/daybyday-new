<script setup>
// 同城：附近帖子（GEO，对应 API.md §3.8）
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getNearbyPosts } from '../api/activity'

const x = ref('116.397')
const y = ref('39.908')
const distance = ref(5000)
const results = ref([])
const loading = ref(false)
const locating = ref(false)

function locate() {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      x.value = pos.coords.longitude.toFixed(6)
      y.value = pos.coords.latitude.toFixed(6)
      locating.value = false
      ElMessage.success('定位成功')
      search()
    },
    () => {
      locating.value = false
      ElMessage.warning('定位失败，可手动输入经纬度')
    },
    { timeout: 8000 }
  )
}

async function search() {
  const xv = Number(x.value)
  const yv = Number(y.value)
  if (Number.isNaN(xv) || Number.isNaN(yv)) {
    ElMessage.warning('请输入正确的经纬度')
    return
  }
  loading.value = true
  try {
    const res = await getNearbyPosts({ x: xv, y: yv, distance: Number(distance.value) })
    results.value = res.data ?? []
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="nearby-page">
    <div class="query-card">
      <h3>📍 同城 · 附近帖子</h3>
      <div class="query-row">
        <el-input v-model="x" placeholder="经度 x" style="width: 140px" />
        <el-input v-model="y" placeholder="纬度 y" style="width: 140px" />
        <el-select v-model="distance" style="width: 140px">
          <el-option label="1 公里内" :value="1000" />
          <el-option label="3 公里内" :value="3000" />
          <el-option label="5 公里内" :value="5000" />
          <el-option label="10 公里内" :value="10000" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">找帖子</el-button>
        <el-button :loading="locating" @click="locate">📍 定位</el-button>
      </div>
    </div>

    <div v-loading="loading" class="result-card">
      <el-empty v-if="!loading && results.length === 0" description="附近没有帖子，发一帖带位置让邻居看到你" />
      <div v-else v-for="p in results" :key="p.id" class="near-item" @click="$router.push(`/post/${p.id}`)">
        <div class="near-title">{{ p.title }}</div>
        <div class="near-meta">{{ p.author?.nickname }}<span v-if="p.barName"> @{{ p.barName }}</span> · 距你 {{ p.distance }} 米</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nearby-page { max-width: 1080px; margin: 16px auto; }
.query-card { background: #fff; border-radius: 6px; padding: 20px; }
.query-card h3 { font-size: 15px; margin-bottom: 14px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.query-row { display: flex; gap: 10px; flex-wrap: wrap; }
.result-card { background: #fff; border-radius: 6px; padding: 20px; margin-top: 16px; min-height: 200px; }
.near-item { padding: 12px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.near-item:hover { background: #f7f9ff; }
.near-title { font-weight: bold; font-size: 14px; margin-bottom: 4px; }
.near-meta { font-size: 12px; color: #999; }
</style>
