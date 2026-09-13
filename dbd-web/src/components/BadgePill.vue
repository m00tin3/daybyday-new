<script setup>
// 限量徽章角标（帖子/楼层的作者昵称旁、主页徽章墙共用）
//
// 配色策略：4 个常用称号用固定配色，保证全站视觉一致、一眼可辨；
// 管理员手工新增的称号按名称码点哈希取色（同名必然同色，不会每次刷新变色）。
import { computed } from 'vue'

const props = defineProps({
  /** 徽章称号 */
  name: { type: String, required: true },
  /** small = 昵称后的角标；large = 徽章墙卡片 */
  size: { type: String, default: 'small' }
})

const PRESET = {
  苦来兮苦宗主: { bg: 'linear-gradient(135deg,#8e1b2e,#c0392b)', icon: '👑' },
  凤川祥: { bg: 'linear-gradient(135deg,#b8860b,#e8b339)', icon: '🦚' },
  苏幽离: { bg: 'linear-gradient(135deg,#5b3f9e,#8e6fd8)', icon: '🌙' },
  千早樱: { bg: 'linear-gradient(135deg,#c2185b,#f06292)', icon: '🌸' }
}

const FALLBACK = [
  { bg: 'linear-gradient(135deg,#1f6feb,#4e9bff)', icon: '🎖' },
  { bg: 'linear-gradient(135deg,#0f7b6c,#2bb3a3)', icon: '🏅' },
  { bg: 'linear-gradient(135deg,#8a5a00,#d99a2b)', icon: '⭐' },
  { bg: 'linear-gradient(135deg,#7a1fa2,#b44fd8)', icon: '💎' }
]

const theme = computed(() => {
  if (PRESET[props.name]) return PRESET[props.name]
  let sum = 0
  for (const ch of props.name) sum += ch.codePointAt(0)
  return FALLBACK[sum % FALLBACK.length]
})
</script>

<template>
  <span class="badge-pill" :class="size" :style="{ background: theme.bg }" :title="`限量徽章：${name}`">
    <span class="ico">{{ theme.icon }}</span>{{ name }}
  </span>
</template>

<style scoped>
.badge-pill {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #fff;
  border-radius: 9px;
  white-space: nowrap;
  vertical-align: middle;
  font-weight: bold;
  text-shadow: 0 1px 1px rgba(0, 0, 0, .18);
}
.badge-pill.small { font-size: 11px; padding: 0 6px; height: 17px; line-height: 17px; }
.badge-pill.large { font-size: 13px; padding: 4px 12px; height: 28px; line-height: 20px; border-radius: 14px; }
.ico { font-size: 10px; }
.badge-pill.large .ico { font-size: 13px; }
</style>
