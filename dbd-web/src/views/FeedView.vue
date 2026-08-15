<script setup>
// 关注 Feed 流（对应 API.md §3.5.2）：ZSet 时间线 + 滚动分页（lastId 游标）
import { ref, onMounted } from 'vue'
import { getFeed } from '../api/feed'
import PostCard from '../components/PostCard.vue'

const posts = ref([])
const lastId = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const inited = ref(false)
const size = 10

async function load() {
  if (loading.value || (inited.value && !hasMore.value)) return
  loading.value = true
  try {
    const res = await getFeed({ lastId: lastId.value, size })
    const data = res.data
    posts.value = posts.value.concat(data?.list ?? [])
    lastId.value = data?.lastId ?? lastId.value
    hasMore.value = data?.hasMore ?? false
    inited.value = true
  } catch {
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="feed-page">
    <div class="feed-head">
      <h3>关注动态</h3>
      <span class="hint">关注的人和吧的新帖，按时间倒序</span>
    </div>
    <section class="feed-list" v-loading="loading && !inited">
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
      <el-empty v-if="inited && posts.length === 0" description="还没有关注任何人和吧，去首页逛逛关注起来吧">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>
    </section>
    <div v-if="posts.length > 0" class="more">
      <el-button v-if="hasMore" :loading="loading" @click="load">加载更多</el-button>
      <span v-else class="end">—— 到底啦 ——</span>
    </div>
  </div>
</template>

<style scoped>
.feed-page { max-width: 1080px; margin: 16px auto; }
.feed-head { background: #fff; border-radius: 6px; padding: 16px 18px; margin-bottom: 12px; }
.feed-head h3 { font-size: 16px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.feed-head .hint { font-size: 12px; color: #999; margin-left: 11px; }
.feed-list { background: #fff; border-radius: 6px; padding: 8px 0; min-height: 300px; }
.more { text-align: center; padding: 16px; }
.more .end { color: #ccc; font-size: 13px; }
</style>
