<script setup>
defineProps({
  title: { type: String, default: '页面建设中' },
  desc: { type: String, default: '该模块待与后端接口联调后填充内容' }
})
</script>

<template>
  <div class="placeholder">
    <el-empty :description="`${title} · ${desc}`">
      <el-tag type="info" size="small">骨架已就位</el-tag>
    </el-empty>
  </div>
</template>

<style scoped>
.placeholder { max-width: 1080px; margin: 80px auto; background: #fff; border-radius: 6px; padding: 40px 0; }
</style>
