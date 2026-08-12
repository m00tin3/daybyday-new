<script setup>
// 帖子列表卡片（首页/吧页/搜索/用户页复用）
// 入参 post：PostVO（含 author/title/content 摘要/计数/createdAt）
defineProps({
  post: { type: Object, required: true }
})

// 摘要：列表接口 content 已是全文，截断展示
function digest(content) {
  if (!content) return ''
  const text = content.replace(/<[^>]+>/g, '')
  return text.length > 90 ? text.slice(0, 90) + '…' : text
}
</script>

<template>
  <div class="post-item" @click="$router.push(`/post/${post.id}`)">
    <div class="post-avatar">{{ post.author?.icon || '👤' }}</div>
    <div class="post-main">
      <div class="post-title">
        <span v-if="post.isTop" class="tag">顶</span>
        <span v-else-if="post.status === 2" class="tag jing">精</span>
        {{ post.title }}
      </div>
      <div class="post-desc">{{ digest(post.content) }}</div>
      <div class="post-meta">
        <span class="user" @click.stop="$router.push(`/user/${post.author?.id}`)">{{ post.author?.nickname }}</span>
        <span v-if="post.barName" class="bar" @click.stop="$router.push(`/bar/${post.barId}`)">@{{ post.barName }}</span>
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
.post-desc { font-size: 13px; color: #999; margin-bottom: 8px; line-height: 1.5; overflow: hidden; }
.post-meta { font-size: 12px; color: #aaa; }
.post-meta .user { color: #4e6ef2; }
.post-meta .user:hover { text-decoration: underline; }
.post-meta .bar { color: #999; margin-left: 8px; }
.post-meta .bar:hover { color: #4e6ef2; }
.post-count { color: #4e6ef2; font-weight: bold; font-size: 13px; }
</style>
