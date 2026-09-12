<script setup>
// 管理后台：帖子管理 + 吧管理（对应 API.md §3.9）
// 说明：真正的鉴权在后端 AdminInterceptor（/api/admin/** 强制 role=1），
// 本页的 403 提示只是兜底体验。
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminPosts, hidePost, restorePost, deletePost,
  getAdminBars, createBar, hideBar, restoreBar, deleteBar
} from '../api/admin'

const tab = ref('post')

/** 帖子状态映射（与后端 Post 常量一致） */
const POST_STATUS = {
  0: { text: '已删除', type: 'danger' },
  1: { text: '正常', type: 'success' },
  2: { text: '精华', type: 'warning' },
  3: { text: '已隐藏', type: 'info' }
}

/* ==================== 帖子管理 ==================== */

const postQuery = reactive({ keyword: '', status: null, page: 1, size: 10 })
const postList = ref([])
const postTotal = ref(0)
const postLoading = ref(false)

async function loadPosts() {
  postLoading.value = true
  try {
    const res = await getAdminPosts({ ...postQuery })
    postList.value = res.data?.list ?? []
    postTotal.value = res.data?.total ?? 0
  } catch {
    postList.value = []
    postTotal.value = 0
  } finally {
    postLoading.value = false
  }
}

function searchPosts() {
  postQuery.page = 1
  loadPosts()
}

function resetPostQuery() {
  postQuery.keyword = ''
  postQuery.status = null
  searchPosts()
}

async function onHidePost(row) {
  try {
    await hidePost(row.id)
    ElMessage.success('已隐藏，前台不再可见')
    loadPosts()
  } catch { /* 拦截器已提示 */ }
}

async function onRestorePost(row) {
  try {
    await restorePost(row.id)
    ElMessage.success('已恢复')
    loadPosts()
  } catch { /* 拦截器已提示 */ }
}

async function onDeletePost(row) {
  try {
    await ElMessageBox.confirm(
      `确定物理删除帖子「${row.title}」？\n该操作会连同其楼层、点赞、收藏一并删除，且不可恢复。`,
      '危险操作确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch { return } // 用户取消
  try {
    await deletePost(row.id)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (postList.value.length === 1 && postQuery.page > 1) postQuery.page--
    loadPosts()
  } catch { /* 拦截器已提示 */ }
}

/* ==================== 吧管理 ==================== */

const barQuery = reactive({ keyword: '', status: null, page: 1, size: 10 })
const barList = ref([])
const barTotal = ref(0)
const barLoading = ref(false)

const createVisible = ref(false)
const createForm = reactive({ name: '', description: '', cover: '' })
const creating = ref(false)

async function loadBars() {
  barLoading.value = true
  try {
    const res = await getAdminBars({ ...barQuery })
    barList.value = res.data?.list ?? []
    barTotal.value = res.data?.total ?? 0
  } catch {
    barList.value = []
    barTotal.value = 0
  } finally {
    barLoading.value = false
  }
}

function searchBars() {
  barQuery.page = 1
  loadBars()
}

function resetBarQuery() {
  barQuery.keyword = ''
  barQuery.status = null
  searchBars()
}

function openCreate() {
  createForm.name = ''
  createForm.description = ''
  createForm.cover = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写吧名称')
    return
  }
  creating.value = true
  try {
    await createBar({ ...createForm, name: createForm.name.trim() })
    ElMessage.success('创建成功')
    createVisible.value = false
    searchBars()
  } catch { /* 拦截器已提示（如名称重复） */ }
  finally { creating.value = false }
}

async function onHideBar(row) {
  try {
    await hideBar(row.id)
    ElMessage.success('已隐藏，前台不再可见')
    loadBars()
  } catch { /* 拦截器已提示 */ }
}

async function onRestoreBar(row) {
  try {
    await restoreBar(row.id)
    ElMessage.success('已恢复')
    loadBars()
  } catch { /* 拦截器已提示 */ }
}

async function onDeleteBar(row) {
  try {
    await ElMessageBox.confirm(
      `确定物理删除贴吧「${row.name}」？\n该吧下全部帖子及其楼层、点赞、收藏都会被一并删除，且不可恢复。`,
      '危险操作确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch { return }
  try {
    await deleteBar(row.id)
    ElMessage.success('已删除')
    if (barList.value.length === 1 && barQuery.page > 1) barQuery.page--
    loadBars()
  } catch { /* 拦截器已提示 */ }
}

function statusText(s) {
  return POST_STATUS[s]?.text ?? s
}
function statusType(s) {
  return POST_STATUS[s]?.type ?? 'info'
}

onMounted(() => {
  loadPosts()
  loadBars()
})
</script>

<template>
  <div class="admin-page">
    <div class="admin-head">
      <h2>管理后台</h2>
      <p class="tip">隐藏 = 改状态、可恢复，前台立刻不可见；删除 = 物理删除，不可恢复。</p>
    </div>

    <el-tabs v-model="tab" class="admin-tabs">
      <!-- ==================== 帖子管理 ==================== -->
      <el-tab-pane label="帖子管理" name="post">
        <div class="toolbar">
          <el-input v-model="postQuery.keyword" placeholder="搜索标题或正文" clearable style="width: 240px"
                    @keyup.enter="searchPosts" />
          <el-select v-model="postQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="正常" :value="1" />
            <el-option label="精华" :value="2" />
            <el-option label="已隐藏" :value="3" />
            <el-option label="已删除" :value="0" />
          </el-select>
          <el-button type="primary" @click="searchPosts">查询</el-button>
          <el-button @click="resetPostQuery">重置</el-button>
        </div>

        <el-table :data="postList" v-loading="postLoading" border stripe size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="barName" label="所属吧" width="120" show-overflow-tooltip />
          <el-table-column label="作者" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.author?.nickname }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="160" />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 3" link type="warning" size="small" @click="onHidePost(row)">隐藏</el-button>
              <el-button v-else link type="success" size="small" @click="onRestorePost(row)">恢复</el-button>
              <el-button link type="primary" size="small" @click="$router.push(`/post/${row.id}`)">查看</el-button>
              <el-button link type="danger" size="small" @click="onDeletePost(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="postTotal" :current-page="postQuery.page" :page-size="postQuery.size"
                       @current-change="(p) => { postQuery.page = p; loadPosts() }" />
      </el-tab-pane>

      <!-- ==================== 吧管理 ==================== -->
      <el-tab-pane label="吧管理" name="bar">
        <div class="toolbar">
          <el-input v-model="barQuery.keyword" placeholder="搜索吧名称" clearable style="width: 240px"
                    @keyup.enter="searchBars" />
          <el-select v-model="barQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="正常" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
          <el-button type="primary" @click="searchBars">查询</el-button>
          <el-button @click="resetBarQuery">重置</el-button>
          <el-button type="success" class="create-btn" @click="openCreate">+ 创建贴吧</el-button>
        </div>

        <el-table :data="barList" v-loading="barLoading" border stripe size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="name" label="吧名称" width="160" show-overflow-tooltip />
          <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip />
          <el-table-column prop="memberCount" label="关注数" width="100" align="right" />
          <el-table-column prop="postCount" label="帖子数" width="100" align="right" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '正常' : '已隐藏' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 1" link type="warning" size="small" @click="onHideBar(row)">隐藏</el-button>
              <el-button v-else link type="success" size="small" @click="onRestoreBar(row)">恢复</el-button>
              <el-button link type="primary" size="small" @click="$router.push(`/bar/${row.id}`)">查看</el-button>
              <el-button link type="danger" size="small" @click="onDeleteBar(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="barTotal" :current-page="barQuery.page" :page-size="barQuery.size"
                       @current-change="(p) => { barQuery.page = p; loadBars() }" />
      </el-tab-pane>
    </el-tabs>

    <!-- 创建贴吧 -->
    <el-dialog v-model="createVisible" title="创建贴吧" width="480px">
      <el-form label-width="80px">
        <el-form-item label="吧名称" required>
          <el-input v-model="createForm.name" maxlength="32" show-word-limit placeholder="例如：摄影吧" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="255" show-word-limit
                    placeholder="一句话介绍这个吧" />
        </el-form-item>
        <el-form-item label="封面 URL">
          <el-input v-model="createForm.cover" maxlength="255" placeholder="可选，图片地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page { max-width: 1200px; margin: 16px auto; }
.admin-head { background: #fff; border-radius: 6px; padding: 16px 20px; margin-bottom: 12px; }
.admin-head h2 { font-size: 18px; margin-bottom: 6px; }
.tip { font-size: 12px; color: #999; }
.admin-tabs { background: #fff; border-radius: 6px; padding: 8px 18px 18px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.create-btn { margin-left: auto; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
