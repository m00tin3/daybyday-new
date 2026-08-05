<script setup>
// 首页：帖子列表 + 热门吧侧栏（视觉参考 tieba-demo）
// 已接后端接口（getPostList / getBarRank）；后端未启动时自动降级展示 mock 数据，保证骨架可演示
import { ref } from 'vue'
import { getPostList } from '../api/post'
import { getBarRank } from '../api/bar'

const loading = ref(true)
const posts = ref([])
const bars = ref([])

// mock 数据：仅在后端不可用时兜底展示
const mockPosts = [
  { id: 1, avatar: '🐱', tag: '热', title: '家人们谁懂啊，我家猫今天居然学会开门了！', desc: '早上起来发现门开了一条缝，监控一看这货凌晨三点自己跳起来把门把手压下去了……', user: '铲屎官小王', time: '10分钟前', count: 256 },
  { id: 2, avatar: '🎮', tag: '精', title: '【攻略】新版本全职业强度排行榜（个人向）', desc: '更新后玩了三天，法师依旧T0，刺客崛起，战士下水道实锤。楼下附详细配装。', user: '游戏老玩家', time: '1小时前', count: 1892 },
  { id: 3, avatar: '🍜', title: '深夜放毒：学校门口那家兰州拉面倒闭了，我好难过', desc: '吃了四年的店，老板说儿子考上公务员接他去大城市享福了。祝老板一切顺利！', user: '干饭魂', time: '3小时前', count: 87 },
  { id: 4, avatar: '📚', title: '考研倒计时150天，开个打卡帖互相监督', desc: '每天早7晚11，图书馆一楼靠窗位置。想一起的留个言，我们组个队互相卷。', user: '上岸人', time: '5小时前', count: 143 },
  { id: 5, avatar: '🚗', title: '第一辆车怎么选？10万预算求推荐', desc: '刚工作两年，预算十万出头，主要上下班通勤+周末自驾游，油车电车都行，求老哥们给点建议。', user: '新手上路', time: '6小时前', count: 321 }
]

const mockBars = [
  { id: 1, name: '猫咪吧', emoji: '🐱', members: '128万', color: '#4e6ef2' },
  { id: 2, name: '游戏攻略吧', emoji: '🎮', members: '96万', color: '#f40' },
  { id: 3, name: '考研上岸吧', emoji: '📚', members: '73万', color: '#2db55d' },
  { id: 4, name: '汽车之家吧', emoji: '🚗', members: '52万', color: '#ff8f1f' }
]

// 加载数据：任一接口失败则用 mock 兜底（Promise.allSettled 互不影响）
async function load() {
  loading.value = true
  try {
    const [postRes, barRes] = await Promise.allSettled([
      getPostList({ page: 1, size: 10 }),
      getBarRank()
    ])
    posts.value = postRes.status === 'fulfilled' ? postRes.value.data ?? [] : mockPosts
    bars.value = barRes.status === 'fulfilled' ? barRes.value.data ?? [] : mockBars
  } finally {
    loading.value = false
  }
}

load()
</script>

<template>
  <div class="container">
    <section class="post-list" v-loading="loading">
      <div v-for="p in posts" :key="p.id" class="post-item" @click="$router.push(`/post/${p.id}`)">
        <div class="post-avatar">{{ p.avatar || '📄' }}</div>
        <div class="post-main">
          <div class="post-title">
            <span v-if="p.tag" class="tag" :class="p.tag">{{ p.tag }}</span>
            <span>{{ p.title }}</span>
          </div>
          <div class="post-desc">{{ p.desc }}</div>
          <div class="post-meta">
            <span class="user">{{ p.user }}</span> · {{ p.time }} · 回复 <span class="post-count">{{ p.count }}</span>
          </div>
        </div>
      </div>
    </section>

    <aside class="sidebar">
      <div class="side-card">
        <h3>热门吧</h3>
        <div v-for="b in bars" :key="b.id" class="side-item" @click="$router.push(`/bar/${b.id}`)">
          <div class="b-avatar" :style="{ background: b.color }">{{ b.emoji }}</div>
          <a>{{ b.name }}</a>
          <span class="members">{{ b.members }}</span>
        </div>
      </div>
      <div class="side-card">
        <h3>热议话题</h3>
        <div class="side-item"><a># 周末去哪玩 #</a><span class="members">热</span></div>
        <div class="side-item"><a># 应届生求职经验 #</a><span class="members">热</span></div>
        <div class="side-item"><a># 一人一句家乡话 #</a><span class="members">新</span></div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.container { max-width: 1080px; margin: 16px auto; display: grid; grid-template-columns: 1fr 260px; gap: 16px; }
.post-list { background: #fff; border-radius: 6px; padding: 8px 0; }
.post-item { display: flex; padding: 14px 18px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.post-item:last-child { border-bottom: none; }
.post-item:hover { background: #f7f9ff; }
.post-avatar { width: 44px; height: 44px; border-radius: 50%; background: #4e6ef2; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20px; margin-right: 14px; flex-shrink: 0; }
.post-main { flex: 1; }
.post-title { font-size: 15px; font-weight: bold; color: #333; margin-bottom: 6px; }
.post-title .tag { font-size: 12px; color: #fff; background: #f40; border-radius: 3px; padding: 1px 5px; margin-right: 6px; font-weight: normal; }
.post-title .tag.精 { background: #2db55d; }
.post-desc { font-size: 13px; color: #999; margin-bottom: 8px; line-height: 1.5; }
.post-meta { font-size: 12px; color: #aaa; }
.post-meta .user { color: #4e6ef2; }
.post-count { color: #4e6ef2; font-weight: bold; font-size: 13px; }

.sidebar { display: flex; flex-direction: column; gap: 16px; }
.side-card { background: #fff; border-radius: 6px; padding: 14px; }
.side-card h3 { font-size: 14px; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #4e6ef2; }
.side-item { display: flex; align-items: center; margin-bottom: 10px; font-size: 13px; cursor: pointer; }
.side-item .b-avatar { width: 30px; height: 30px; border-radius: 6px; margin-right: 10px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 14px; flex-shrink: 0; }
.side-item a { color: #333; text-decoration: none; }
.side-item:hover a { color: #4e6ef2; }
.side-item .members { margin-left: auto; color: #aaa; font-size: 12px; }
</style>
