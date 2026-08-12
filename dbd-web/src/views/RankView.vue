<script setup>
// 排行榜：热帖榜（ZSet 热度）+ 热吧榜（ZSet 关注数）（对应 API.md §3.5/§3.3.5）
import { ref, onMounted } from 'vue'
import { getHotPosts } from '../api/user'
import { getBarRank } from '../api/bar'
import PostCard from '../components/PostCard.vue'

const tab = ref('post')
const hotPosts = ref([])
const hotBars = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    if (tab.value === 'post') {
      const res = await getHotPosts()
      hotPosts.value = res.data ?? []
    } else {
      const res = await getBarRank()
      hotBars.value = res.data ?? []
    }
  } catch { /* 后端不可用保持空 */ } finally {
    loading.value = false
  }
}

function switchTab(t) {
  tab.value = t
  load()
}

function medal(i) {
  return ['🥇', '🥈', '🥉'][i] || `${i + 1}`
}

onMounted(load)
</script>

<template>
  <div class="rank-page">
    <el-tabs v-model="tab" class="rank-tabs" @tab-change="switchTab">
      <el-tab-pane label="热帖榜" name="post" />
      <el-tab-pane label="热吧榜" name="bar" />
    </el-tabs>

    <div v-loading="loading" class="rank-list">
      <template v-if="tab === 'post'">
        <PostCard v-for="p in hotPosts" :key="p.id" :post="p" />
        <el-empty v-if="!loading && hotPosts.length === 0" description="暂无热帖数据" />
      </template>
      <template v-else>
        <div v-for="(b, i) in hotBars" :key="b.id" class="bar-item" @click="$router.push(`/bar/${b.id}`)">
          <div class="medal" :class="{ top3: i < 3 }">{{ medal(i) }}</div>
          <div class="bar-avatar" :class="`color-${i % 4}`">{{ b.name.slice(0, 1) }}</div>
          <div class="bar-main">
            <div class="bar-name">{{ b.name }}</div>
            <div class="bar-desc">{{ b.description || '' }}</div>
          </div>
          <div class="bar-members">关注 {{ b.memberCount }}</div>
        </div>
        <el-empty v-if="!loading && hotBars.length === 0" description="暂无热吧数据" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.rank-page { max-width: 1080px; margin: 16px auto; background: #fff; border-radius: 6px; padding: 8px 18px; }
.rank-tabs { padding: 0 10px; }
.rank-list { min-height: 300px; }
.bar-item { display: flex; align-items: center; padding: 12px 8px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.bar-item:hover { background: #f7f9ff; }
.medal { width: 40px; text-align: center; font-size: 16px; color: #aaa; flex-shrink: 0; }
.medal.top3 { font-size: 20px; }
.bar-avatar { width: 40px; height: 40px; border-radius: 8px; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; margin-right: 12px; flex-shrink: 0; }
.color-0 { background: #4e6ef2; }
.color-1 { background: #f40; }
.color-2 { background: #2db55d; }
.color-3 { background: #ff8f1f; }
.bar-main { flex: 1; min-width: 0; }
.bar-name { font-weight: bold; font-size: 14px; }
.bar-desc { font-size: 12px; color: #999; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bar-members { color: #4e6ef2; font-size: 13px; }
</style>
