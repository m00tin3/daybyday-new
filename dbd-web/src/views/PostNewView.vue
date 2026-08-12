<script setup>
// 发帖页：选择吧 + 标题 + 正文（对应 API.md §3.2.3）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBarRank } from '../api/bar'
import { createPost } from '../api/post'

const router = useRouter()
const bars = ref([])
const barId = ref(null)
const title = ref('')
const content = ref('')
const submitting = ref(false)

onMounted(async () => {
  try {
    const res = await getBarRank()
    bars.value = res.data ?? []
  } catch { /* 无吧列表时仍可输入 */ }
})

async function submit() {
  if (!barId.value) {
    ElMessage.warning('请选择发布的吧')
    return
  }
  if (!title.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写内容')
    return
  }
  submitting.value = true
  try {
    const res = await createPost({ barId: barId.value, title: title.value.trim(), content: content.value.trim() })
    ElMessage.success(res.msg || '发布成功')
    router.push(`/post/${res.data.id}`)
  } catch { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="new-post">
    <el-card>
      <h2 class="page-title">发布新帖</h2>
      <el-form label-position="top">
        <el-form-item label="选择吧">
          <el-select v-model="barId" placeholder="选择要发布的吧" style="width: 240px">
            <el-option v-for="b in bars" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="title" maxlength="64" show-word-limit placeholder="一句话说清楚主题（最多 64 字）" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="content" type="textarea" :rows="10" maxlength="50000" show-word-limit placeholder="写点什么…" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">发布</el-button>
          <el-button size="large" @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.new-post { max-width: 760px; margin: 24px auto; }
.page-title { color: #4e6ef2; margin-bottom: 20px; }
</style>
