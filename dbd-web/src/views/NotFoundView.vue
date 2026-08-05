<template>
  <div class="not-found">
    <el-result icon="warning" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.not-found { max-width: 1080px; margin: 60px auto; }
</style>
