<script setup>
// 徽章墙（个人中心 / 他人主页共用）
//
// 纯展示组件：数据由父组件按场景取（自己走 /activity/my/badges，别人走
// /activity/user/{id}/badges），组件不关心来源，避免两处重复实现。
import BadgePill from './BadgePill.vue'

defineProps({
  /** BadgeVO 数组：{ activityId, badgeName, title, awardDesc, awardedAt } */
  badges: { type: Array, default: () => [] },
  /** 是否展示空状态引导（他人主页为空时不必劝他去抢） */
  showEmptyHint: { type: Boolean, default: true }
})
</script>

<template>
  <div class="badge-wall">
    <h3 class="wall-title">
      限量徽章
      <span class="count">{{ badges.length }}</span>
    </h3>

    <div v-if="badges.length" class="wall-body">
      <div v-for="b in badges" :key="b.activityId" class="wall-item">
        <BadgePill :name="b.badgeName" size="large" />
        <span class="date">{{ (b.awardedAt || '').slice(0, 10) }}</span>
      </div>
    </div>

    <p v-else class="empty">
      {{ showEmptyHint ? '还没有限量徽章' : '暂无徽章' }}
      <el-link v-if="showEmptyHint" type="primary" :underline="false" class="go" @click="$router.push('/activity')">
        去抢夺 →
      </el-link>
    </p>
  </div>
</template>

<style scoped>
.badge-wall { background: #fff; border-radius: 6px; padding: 16px; }
.wall-title { font-size: 14px; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.count { color: #b8860b; font-size: 13px; background: #fdf6e3; border-radius: 8px; padding: 0 7px; }
.wall-body { display: flex; flex-wrap: wrap; gap: 10px 14px; }
.wall-item { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; }
.date { font-size: 10px; color: #bbb; padding-left: 2px; }
.empty { font-size: 12px; color: #bbb; }
.empty .go { font-size: 12px; margin-left: 4px; }
</style>
