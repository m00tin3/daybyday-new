<script setup>
// 帖子列表卡片（首页/吧页/搜索/用户页复用）
// 入参 post：PostVO（含 author/title/content 摘要/计数/createdAt）
// author.badges 为作者已获得的限量徽章称号（后端批量填充），可能为空数组
import BadgePill from './BadgePill.vue'

defineProps({
  post: { type: Object, required: true }
})

// 摘要：列表接口 content 已是全文，截断展示
function digest(content) {
  if (!content) return ''
  const text = content.replace(/<[^>]+>/g, '')
  return text.length > 90 ? text.slice(0, 90) + '…' : text
}

// 徽章多的时候列表里只展示前 2 个，其余折叠成 +N
function shownBadges(badges) {
  return (badges || []).slice(0, 2)
}
function restBadgeCount(badges) {
  return Math.max(0, (badges || []).length - 2)
}
</script>

<template>
  <div class="post-item" @click="$router.push(`/post/${post.id}`)">
    <div class="post-avatar">{{ post.author?.icon || '👤' }}</div>
    <div class="post-main">
      <div class="post-title">
        <!-- 公告的 is_top 恒为 1，必须排在「顶」之前判断，否则永远显示成置顶帖 -->
        <span v-if="post.type === 1" class="tag notice">公告</span>
        <span v-else-if="post.isTop" class="tag">顶</span>
        <span v-else-if="post.status === 2" class="tag jing">精</span>
        {{ post.title }}
      </div>
      <div class="post-desc">{{ digest(post.content) }}</div>
      <div class="post-meta">
        <span class="user" @click.stop="$router.push(`/user/${post.author?.id}`)">{{ post.author?.nickname }}</span>
        <BadgePill
          v-for="b in shownBadges(post.author?.badges)"
          :key="b"
          :name="b"
          class="meta-badge"
          @click.stop
        />
        <span v-if="restBadgeCount(post.author?.badges)" class="badge-more" :title="post.author.badges.slice(2).join('、')">
          +{{ restBadgeCount(post.author?.badges) }}
        </span>
        <span v-if="post.barName" class="bar" @click.stop="$router.push(`/bar/${post.barId}`)">@{{ post.barName }}</span>
        <span v-if="post.city" class="city" @click.stop="$router.push('/city')">🏙 {{ post.city }}</span>
        · {{ post.createdAt }} ·
        回复 <span class="post-count">{{ post.commentCount }}</span>
        <span class="post-count" style="margin-left: 10px">👍 {{ post.likeCount }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-item { display: flex; padding: 14px 18px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.post-item:hover { background: #f7f9ff; }
.post-avatar { width: 44px; height: 44px; border-radius: 50%; background: #4e6ef2; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20px; margin-right: 14px; flex-shrink: 0; }
.post-main { flex: 1; min-width: 0; }
.post-title { font-size: 15px; font-weight: bold; color: #333; margin-bottom: 6px; }
.post-title .tag { font-size: 12px; color: #fff; background: #f40; border-radius: 3px; padding: 1px 5px; margin-right: 6px; font-weight: normal; }
.post-title .tag.jing { background: #2db55d; }
/* 公告：官方口径，用深红与「顶」的橙红区分开 */
.post-title .tag.notice { background: #b91c1c; }
.post-desc { font-size: 13px; color: #999; margin-bottom: 8px; line-height: 1.5; overflow: hidden; }
.post-meta { font-size: 12px; color: #aaa; }
.post-meta .user { color: #4e6ef2; }
.post-meta .user:hover { text-decoration: underline; }
.post-meta .bar { color: #999; margin-left: 8px; }
.post-meta .bar:hover { color: #4e6ef2; }
.post-meta .city { color: #999; margin-left: 8px; }
.post-meta .city:hover { color: #4e6ef2; }
.post-meta .meta-badge { margin-left: 5px; }
.post-meta .badge-more { margin-left: 4px; color: #b8860b; font-weight: bold; cursor: help; }
.post-count { color: #4e6ef2; font-weight: bold; font-size: 13px; }
</style>
