<script setup>
// 发帖页：选择吧 + 标题 + 正文 + 城市（对应 API.md §3.2.3）
//
// 变更说明：原先这里是「可选经纬度」（GEO 同城）。因缺少地图 SDK，无法把用户
// 输入的地址转成经纬度，要求手输坐标体验差且无法校验，故改为手动填写城市；
// GEO 相关代码保留但功能已封存。
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
const city = ref('')
const submitting = ref(false)

/** 常用城市快捷标签：点一下填入，也可手打任意城市 */
const HOT_CITIES = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '西安', '重庆']

function pickCity(c) {
  // 再次点击已选中的标签则取消选择
  city.value = city.value === c ? '' : c
}

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
  const payload = { barId: barId.value, title: title.value.trim(), content: content.value.trim() }
  // 城市可选：留空则不入库，帖子不会出现在任何城市的筛选结果里
  if (city.value.trim() !== '') {
    payload.city = city.value.trim()
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
        <el-form-item label="城市（可选，填了才能被「城市」页检索到）">
          <div class="city-row">
            <el-input v-model="city" placeholder="如：北京" maxlength="32" style="width: 200px" clearable />
            <div class="city-tags">
              <el-tag
                v-for="c in HOT_CITIES"
                :key="c"
                class="city-tag"
                :effect="city === c ? 'dark' : 'plain'"
                @click="pickCity(c)"
              >{{ c }}</el-tag>
            </div>
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
.city-row { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.city-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.city-tag { cursor: pointer; user-select: none; }
</style>
