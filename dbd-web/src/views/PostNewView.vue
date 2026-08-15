<script setup>
// 发帖页：选择吧 + 标题 + 正文 + 可选位置（对应 API.md §3.2.3；带坐标则写入 GEO 同城）
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
const x = ref('')
const y = ref('')
const locating = ref(false)
const submitting = ref(false)

onMounted(async () => {
  try {
    const res = await getBarRank()
    bars.value = res.data ?? []
  } catch { /* 无吧列表时仍可输入 */ }
})

/** 浏览器定位（可选，失败可手动输入） */
function locate() {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      x.value = pos.coords.longitude.toFixed(6)
      y.value = pos.coords.latitude.toFixed(6)
      locating.value = false
      ElMessage.success('定位成功，发帖后将出现在附近帖子')
    },
    () => {
      locating.value = false
      ElMessage.warning('定位失败，可手动输入经纬度')
    },
    { timeout: 8000 }
  )
}

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
  // 坐标：可都不填；都填则需合法数字
  const payload = { barId: barId.value, title: title.value.trim(), content: content.value.trim() }
  const hasX = x.value.trim() !== ''
  const hasY = y.value.trim() !== ''
  if (hasX || hasY) {
    const xv = Number(x.value)
    const yv = Number(y.value)
    if (!hasX || !hasY || Number.isNaN(xv) || Number.isNaN(yv)) {
      ElMessage.warning('请同时填写正确的经纬度，或都留空')
      return
    }
    payload.x = xv
    payload.y = yv
  }
  submitting.value = true
  try {
    const res = await createPost(payload)
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
        <el-form-item label="位置（可选，发帖后进入同城·附近帖子）">
          <div class="geo-row">
            <el-input v-model="x" placeholder="经度，如 116.397" style="width: 170px" />
            <el-input v-model="y" placeholder="纬度，如 39.908" style="width: 170px" />
            <el-button :loading="locating" @click="locate">📍 使用我的位置</el-button>
          </div>
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
.geo-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
</style>
