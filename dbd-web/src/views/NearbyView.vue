<script setup>
// 同城：附近帖子（GEO，对应 API.md §3.8）
// 后端 GEO 接口属于阶段三，当前以降级 UI 展示，接口就绪后自动生效
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getNearbyPosts } from '../api/activity'

const x = ref('116.397')
const y = ref('39.908')
const distance = ref(5000)
const results = ref([])
const loading = ref(false)
const available = ref(true)

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
    available.value = true
  } catch {
    available.value = false
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
      </div>
    </div>

    <div v-loading="loading" class="result-card">
      <el-empty v-if="!loading && !available" description="同城模块（GEO）开发中 · 阶段三上线">
        <p class="tip">后端将基于 Redis GEO（GEOSEARCH）实现附近帖子检索，敬请期待</p>
      </el-empty>
      <el-empty v-else-if="!loading && results.length === 0" description="附近没有帖子，发一帖让邻居看到你" />
      <div v-else v-for="p in results" :key="p.id" class="near-item" @click="$router.push(`/post/${p.id}`)">
        <div class="near-title">{{ p.title }}</div>
        <div class="near-meta">{{ p.author?.nickname }} · 距你 {{ p.distance }} 米</div>
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
.tip { color: #999; font-size: 13px; margin-top: 8px; }
.near-item { padding: 12px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.near-item:hover { background: #f7f9ff; }
.near-title { font-weight: bold; font-size: 14px; margin-bottom: 4px; }
.near-meta { font-size: 12px; color: #999; }
</style>
