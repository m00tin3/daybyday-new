<script setup>
// 首页：帖子列表 + 热门吧/热议侧栏（视觉参考 tieba-demo）
import { ref } from 'vue'
import { getPostList } from '../api/post'
import { getBarRank } from '../api/bar'
import PostCard from '../components/PostCard.vue'

const loading = ref(true)
const posts = ref([])
const bars = ref([])
const page = ref(1)
const total = ref(0)
const size = 10

// mock 数据：仅在后端不可用时兜底展示
const mockPosts = [
  { id: 1, avatar: '🐱', tag: '热', title: '家人们谁懂啊，我家猫今天居然学会开门了！', desc: '早上起来发现门开了一条缝，监控一看这货凌晨三点自己跳起来把门把手压下去了……', user: '铲屎官小王', time: '10分钟前', count: 256 },
  { id: 2, avatar: '🎮', tag: '精', title: '【攻略】新版本全职业强度排行榜（个人向）', desc: '更新后玩了三天，法师依旧T0，刺客崛起，战士下水道实锤。楼下附详细配装。', user: '游戏老玩家', time: '1小时前', count: 1892 },
  { id: 3, avatar: '🍜', title: '深夜放毒：学校门口那家兰州拉面倒闭了，我好难过', desc: '吃了四年的店，老板说儿子考上公务员接他去大城市享福了。祝老板一切顺利！', user: '干饭魂', time: '3小时前', count: 87 },
  { id: 4, avatar: '📚', title: '考研倒计时150天，开个打卡帖互相监督', desc: '每天早7晚11，图书馆一楼靠窗位置。想一起的留个言，我们组个队互相卷。', user: '上岸人', time: '5小时前', count: 143 },
  { id: 5, avatar: '🚗', title: '第一辆车怎么选？10万预算求推荐', desc: '刚工作两年，预算十万出头，主要上下班通勤+周末自驾游，油车电车都行，求老哥们给点建议。', user: '新手上路', time: '6小时前', count: 321 }
]

const mockBars = [
  { id: 1, name: '猫咪吧', emoji: '🐱', memberCount: '128万', color: '#4e6ef2' },
  { id: 2, name: '游戏攻略吧', emoji: '🎮', memberCount: '96万', color: '#f40' },
  { id: 3, name: '考研上岸吧', emoji: '📚', memberCount: '73万', color: '#2db55d' },
  { id: 4, name: '汽车之家吧', emoji: '🚗', memberCount: '52万', color: '#ff8f1f' }
]

const barColors = ['#4e6ef2', '#f40', '#2db55d', '#ff8f1f']

async function load() {
  loading.value = true
  try {
    const res = await getPostList({ page: page.value, size })
    posts.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } catch {
    posts.value = mockPosts
  } finally {
    loading.value = false
  }
}

async function loadBars() {
  try {
    const res = await getBarRank()
    bars.value = res.data ?? mockBars
  } catch {
    bars.value = mockBars
  }
}

load()
loadBars()
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
      <el-empty v-if="!loading && posts.length === 0" description="还没有帖子，快去发第一帖吧" />
    </section>

    <aside class="sidebar">
      <div class="side-card">
        <h3>热门吧</h3>
        <div v-for="(b, i) in bars" :key="b.id" class="side-item" @click="$router.push(`/bar/${b.id}`)">
          <div class="b-avatar" :style="{ background: barColors[i % 4] }">{{ b.name.slice(0, 1) }}</div>
          <a>{{ b.name }}</a>
          <span class="members">{{ b.memberCount }}</span>
        </div>
      </div>
      <div class="side-card">
        <h3>发现</h3>
        <div class="side-item" @click="$router.push('/rank')"><a>🏆 排行榜</a></div>
        <div class="side-item" @click="$router.push('/search')"><a>🔥 热搜</a></div>
        <div class="side-item" @click="$router.push('/nearby')"><a>📍 同城</a></div>
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
</style>
