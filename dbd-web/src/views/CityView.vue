<script setup>
// 按城市浏览（替代已封存的 GEO「同城」）
//
// 背景：原「同城」依赖经纬度 + Redis GEO，需要地图 SDK 做地址→坐标转换；
// 拿不到 SDK 时只能让用户手输坐标，体验差且无法校验，因此改为按城市浏览。
//
// 数据来源：
//   GET /api/post/cities     有帖子的城市及数量（后端 Redis 缓存）
//   GET /api/post/list?city= 该城市的帖子（分页）
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPostList, getPostCities } from '../api/post'
import PostCard from '../components/PostCard.vue'

/** 常用城市快捷标签 */
const HOT_CITIES = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '西安', '重庆']

const city = ref('')
const cities = ref([])
const posts = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const searched = ref(false)

async function loadCities() {
  try {
    const res = await getPostCities()
    cities.value = res.data ?? []
  } catch {
    cities.value = []
  }
}

/** 查询：传 cityName 时先切换城市（点标签用） */
async function search(cityName, p) {
  if (typeof cityName === 'string') city.value = cityName
  const target = city.value.trim()
  if (!target) {
    ElMessage.warning('请输入或选择一个城市')
    return
  }
  page.value = p ?? 1
  loading.value = true
  searched.value = true
  try {
    const res = await getPostList({ city: target, page: page.value, size })
    posts.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    posts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(loadCities)
</script>

<template>
  <div class="city-page">
    <!-- 城市选择 -->
    <div class="card">
      <h3>🏙 按城市浏览</h3>
      <div class="search-row">
        <el-input
          v-model="city"
          placeholder="输入城市，如：北京"
          maxlength="32"
          clearable
          style="width: 220px"
          @keyup.enter="search()"
        />
        <el-button type="primary" @click="search()">查找帖子</el-button>
      </div>

      <div class="tags">
        <span class="tags-label">常用</span>
        <el-tag
          v-for="c in HOT_CITIES"
          :key="c"
          class="tag"
          :effect="city === c ? 'dark' : 'plain'"
          @click="search(c)"
        >{{ c }}</el-tag>
      </div>

      <div v-if="cities.length" class="tags">
        <span class="tags-label">有帖子</span>
        <el-tag
          v-for="c in cities"
          :key="c.city"
          class="tag"
          type="success"
          :effect="city === c.city ? 'dark' : 'plain'"
          @click="search(c.city)"
        >{{ c.city }} · {{ c.postCount }}</el-tag>
      </div>
      <p v-else class="hint">还没有帖子填写过城市——发帖时填写城市即可出现在这里</p>
    </div>

    <!-- 结果 -->
    <div v-if="searched" class="card result">
      <h3>{{ city }} 的帖子 <span class="count">{{ total }}</span></h3>
      <div v-loading="loading">
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
        <el-empty v-if="!loading && posts.length === 0" :description="`${city} 还没有帖子`" />
      </div>
      <el-pagination
        v-if="total > size"
        class="pager"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p) => search(undefined, p)"
      />
    </div>
  </div>
</template>

<style scoped>
.city-page { max-width: 1080px; margin: 16px auto; }
.card { background: #fff; border-radius: 6px; padding: 16px 18px; margin-bottom: 16px; }
.card h3 { font-size: 15px; margin-bottom: 14px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.search-row { display: flex; gap: 10px; margin-bottom: 14px; }
.tags { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 10px; }
.tags-label { font-size: 12px; color: #999; margin-right: 2px; }
.tag { cursor: pointer; user-select: none; }
.hint { font-size: 12px; color: #bbb; margin-top: 4px; }
.result { padding: 0 18px 14px; }
.result h3 { margin: 16px 0 8px; }
.count { color: #4e6ef2; font-weight: bold; }
.pager { padding: 12px 0; justify-content: center; }
</style>
