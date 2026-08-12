<script setup>
// 搜索页：关键词搜索 + 热搜词（对应 API.md §3.7）
import { ref, onMounted } from 'vue'
import { getHotSearch, searchPosts } from '../api/activity'
import PostCard from '../components/PostCard.vue'

const keyword = ref('')
const hotWords = ref([])
const results = ref([])
const searched = ref(false)
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadHot() {
  try {
    const res = await getHotSearch()
    hotWords.value = res.data ?? []
  } catch { hotWords.value = [] }
}

async function search(kw) {
  const kwText = (kw ?? keyword.value).trim()
  if (!kwText) return
  keyword.value = kwText
  page.value = 1
  await doSearch()
}

async function doSearch() {
  if (!keyword.value.trim()) return
  loading.value = true
  searched.value = true
  try {
    const res = await searchPosts(keyword.value.trim(), { page: page.value, size: size.value })
    results.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
    loadHot()
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadHot)
</script>

<template>
  <div class="search-page">
    <div class="search-card">
      <div class="search-row">
        <el-input v-model="keyword" size="large" placeholder="搜索感兴趣的内容" clearable @keyup.enter="search()">
          <template #append>
            <el-button :icon="'Search'" @click="search()">搜索</el-button>
          </template>
        </el-input>
      </div>
      <div class="hot-row">
        <span class="hot-label">热搜：</span>
        <el-tag
          v-for="(w, i) in hotWords"
          :key="w"
          class="hot-tag"
          :type="i < 3 ? 'danger' : 'info'"
          effect="light"
          round
          @click="search(w)"
        >
          {{ i + 1 }}. {{ w }}
        </el-tag>
        <span v-if="hotWords.length === 0" class="no-hot">暂无热搜，搜点什么让它热起来</span>
      </div>
    </div>

    <div v-if="searched" v-loading="loading" class="result-list">
      <div class="result-head">
        <h3>「{{ keyword }}」的搜索结果（{{ total }}）</h3>
      </div>
      <PostCard v-for="p in results" :key="p.id" :post="p" />
      <el-pagination
        v-if="total > size"
        class="pager"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p) => { page = p; doSearch() }"
      />
      <el-empty v-if="!loading && results.length === 0" description="没有找到相关帖子" />
    </div>
  </div>
</template>

<style scoped>
.search-page { max-width: 1080px; margin: 16px auto; }
.search-card { background: #fff; border-radius: 6px; padding: 20px; }
.search-row { max-width: 560px; }
.hot-row { margin-top: 14px; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.hot-label { font-size: 13px; color: #999; }
.hot-tag { cursor: pointer; }
.no-hot { font-size: 12px; color: #bbb; }
.result-list { background: #fff; border-radius: 6px; padding: 8px 0; margin-top: 16px; }
.result-head { padding: 10px 18px; border-bottom: 1px solid #f0f0f0; }
.result-head h3 { font-size: 14px; }
.pager { padding: 12px 18px; justify-content: center; }
</style>
