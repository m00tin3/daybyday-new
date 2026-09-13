<script setup>
// 管理后台：帖子管理 + 吧管理（对应 API.md §3.9）
// 说明：真正的鉴权在后端 AdminInterceptor（/api/admin/** 强制 role=1），
// 本页的 403 提示只是兜底体验。
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminPosts, hidePost, restorePost, deletePost, topPost, untopPost, createNotice,
  getAdminBars, createBar, hideBar, restoreBar, deleteBar,
  getAdminActivities, createActivity, updateActivity, endActivity, deleteActivity
} from '../api/admin'
import BadgePill from '../components/BadgePill.vue'

const tab = ref('post')

/** 常用徽章称号：下拉里可直接选，也允许管理员手输新称号（allow-create） */
const PRESET_BADGES = ['凤川祥', '苏幽离', '千早樱', '苦来兮苦宗主']

/** 活动状态映射（由后端按时间动态计算：0未开始 1进行中 2已结束） */
const ACTIVITY_STATUS = {
  0: { text: '未开始', type: 'warning' },
  1: { text: '进行中', type: 'success' },
  2: { text: '已结束', type: 'info' }
}

/** 帖子状态映射（与后端 Post 常量一致） */
const POST_STATUS = {
  0: { text: '已删除', type: 'danger' },
  1: { text: '正常', type: 'success' },
  2: { text: '精华', type: 'warning' },
  3: { text: '已隐藏', type: 'info' }
}

/** 帖子类型映射（与后端 Post.TYPE_* 一致；公告不挂吧、恒置顶） */
const POST_TYPE = {
  0: { text: '普通帖', type: 'info' },
  1: { text: '公告', type: 'danger' }
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

// 置顶是全站生效的，可以置顶任何人的帖子（改 post.is_top）
async function onTopPost(row) {
  try {
    await topPost(row.id)
    ElMessage.success('已置顶，首页立刻生效')
    loadPosts()
  } catch { /* 拦截器已提示 */ }
}

async function onUntopPost(row) {
  try {
    await untopPost(row.id)
    ElMessage.success('已取消置顶')
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

/* ==================== 限量徽章活动管理 ==================== */

const actQuery = reactive({ keyword: '', status: null, page: 1, size: 10 })
const actList = ref([])
const actTotal = ref(0)
const actLoading = ref(false)

const actVisible = ref(false)
const actSaving = ref(false)
/** 编辑中的活动 ID；null 表示"发布新活动" */
const actEditingId = ref(null)
const actForm = reactive({
  badgeName: '',
  stock: 10,
  beginTime: '',
  endTime: '',
  awardDesc: ''
})

async function loadActivities() {
  actLoading.value = true
  try {
    const res = await getAdminActivities({ ...actQuery })
    actList.value = res.data?.list ?? []
    actTotal.value = res.data?.total ?? 0
  } catch {
    actList.value = []
    actTotal.value = 0
  } finally {
    actLoading.value = false
  }
}

function searchActivities() {
  actQuery.page = 1
  loadActivities()
}

function resetActQuery() {
  actQuery.keyword = ''
  actQuery.status = null
  searchActivities()
}

/** 本地时间 → 'YYYY-MM-DD HH:mm:ss'（后端按这个格式解析，不用 toISOString 以免转成 UTC） */
function fmt(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function openCreateActivity() {
  const now = new Date()
  const week = new Date(now.getTime() + 7 * 24 * 3600 * 1000)
  actEditingId.value = null
  actForm.badgeName = ''
  actForm.stock = 10
  actForm.beginTime = fmt(now)
  actForm.endTime = fmt(week)
  actForm.awardDesc = ''
  actVisible.value = true
}

function openEditActivity(row) {
  actEditingId.value = row.id
  actForm.badgeName = row.badgeName || ''
  actForm.stock = row.stock
  actForm.beginTime = row.beginTime
  actForm.endTime = row.endTime
  actForm.awardDesc = row.awardDesc || ''
  actVisible.value = true
}

async function submitActivity() {
  const name = actForm.badgeName.trim()
  if (!name) {
    ElMessage.warning('请填写徽章称号')
    return
  }
  if (!actForm.beginTime || !actForm.endTime) {
    ElMessage.warning('请选择开始与结束时间')
    return
  }
  actSaving.value = true
  const payload = {
    badgeName: name,
    stock: Number(actForm.stock),
    beginTime: actForm.beginTime,
    endTime: actForm.endTime,
    awardDesc: actForm.awardDesc.trim()
  }
  try {
    if (actEditingId.value) {
      await updateActivity(actEditingId.value, payload)
      ElMessage.success('已保存（已抢到的份数保持不变）')
    } else {
      await createActivity(payload)
      ElMessage.success('发布成功')
    }
    actVisible.value = false
    searchActivities()
  } catch { /* 拦截器已提示（称号重复 / 时间不合法等） */ }
  finally { actSaving.value = false }
}

async function onEndActivity(row) {
  try {
    await ElMessageBox.confirm(
      `确定提前结束「${row.badgeName}」？\n活动将立刻不可再抢，但已经抢到的人徽章仍然保留。`,
      '确认结束',
      { type: 'warning', confirmButtonText: '确认结束', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await endActivity(row.id)
    ElMessage.success('已结束')
    loadActivities()
  } catch { /* 拦截器已提示 */ }
}

async function onDeleteActivity(row) {
  try {
    await ElMessageBox.confirm(
      `确定物理删除活动「${row.badgeName}」？\n已发放的 ${row.awardedCount || 0} 枚徽章会连同领取记录一起消失，且不可恢复。`,
      '危险操作确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch { return }
  try {
    await deleteActivity(row.id)
    ElMessage.success('已删除')
    if (actList.value.length === 1 && actQuery.page > 1) actQuery.page--
    loadActivities()
  } catch { /* 拦截器已提示 */ }
}

function statusText(s) {
  return POST_STATUS[s]?.text ?? s
}
function statusType(s) {
  return POST_STATUS[s]?.type ?? 'info'
}

function typeText(t) {
  return POST_TYPE[t ?? 0]?.text ?? '普通帖'
}

/* ==================== 公告管理 ==================== */

// 公告本身是帖子（type=1、不挂吧、恒置顶），所以列表直接复用帖子管理接口，
// 只是固定带上 type=1 —— 不再单独开一套 /admin/notice/list
const noticeQuery = reactive({ keyword: '', page: 1, size: 10 })
const noticeList = ref([])
const noticeTotal = ref(0)
const noticeLoading = ref(false)

async function loadNotices() {
  noticeLoading.value = true
  try {
    const res = await getAdminPosts({ ...noticeQuery, type: 1 })
    noticeList.value = res.data?.list ?? []
    noticeTotal.value = res.data?.total ?? 0
  } catch {
    noticeList.value = []
    noticeTotal.value = 0
  } finally {
    noticeLoading.value = false
  }
}

function searchNotices() {
  noticeQuery.page = 1
  loadNotices()
}

function resetNoticeQuery() {
  noticeQuery.keyword = ''
  searchNotices()
}

const noticeVisible = ref(false)
const noticeSaving = ref(false)
const noticeForm = reactive({ title: '', content: '' })

function openCreateNotice() {
  noticeForm.title = ''
  noticeForm.content = ''
  noticeVisible.value = true
}

async function submitNotice() {
  if (!noticeForm.title.trim()) {
    ElMessage.warning('请填写公告标题')
    return
  }
  if (!noticeForm.content.trim()) {
    ElMessage.warning('请填写公告内容')
    return
  }
  noticeSaving.value = true
  try {
    await createNotice({
      title: noticeForm.title.trim(),
      content: noticeForm.content.trim()
    })
    ElMessage.success('公告已发布，已置顶到首页最前')
    noticeVisible.value = false
    searchNotices()
  } catch { /* 拦截器已提示 */ } finally {
    noticeSaving.value = false
  }
}

/** 公告删除后两个列表都可能受影响（公告也在帖子列表里），一起刷新 */
async function onDeleteNotice(row) {
  const before = noticeList.value.length
  try {
    await ElMessageBox.confirm(
      `确定物理删除公告「${row.title}」？\n该操作会连同其楼层、点赞、收藏一并删除，且不可恢复。`,
      '危险操作确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch { return } // 用户取消
  try {
    await deletePost(row.id)
    ElMessage.success('已删除')
    if (before === 1 && noticeQuery.page > 1) noticeQuery.page--
    loadNotices()
    loadPosts()
  } catch { /* 拦截器已提示 */ }
}

onMounted(() => {
  loadPosts()
  loadNotices()
  loadBars()
  loadActivities()
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
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="POST_TYPE[row.type ?? 0]?.type" size="small">{{ typeText(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="所属吧" width="120" show-overflow-tooltip>
            <!-- 公告不挂吧，barName 为空 -->
            <template #default="{ row }">{{ row.barName || '—' }}</template>
          </el-table-column>
          <el-table-column label="作者" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.author?.nickname }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="160" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 3" link type="warning" size="small" @click="onHidePost(row)">隐藏</el-button>
              <el-button v-else link type="success" size="small" @click="onRestorePost(row)">恢复</el-button>
              <el-button v-if="!row.isTop" link type="primary" size="small" @click="onTopPost(row)">置顶</el-button>
              <el-button v-else link type="info" size="small" @click="onUntopPost(row)">取消置顶</el-button>
              <el-button link type="primary" size="small" @click="$router.push(`/post/${row.id}`)">查看</el-button>
              <el-button link type="danger" size="small" @click="onDeletePost(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="postTotal" :current-page="postQuery.page" :page-size="postQuery.size"
                       @current-change="(p) => { postQuery.page = p; loadPosts() }" />
      </el-tab-pane>

      <!-- ==================== 公告管理 ==================== -->
      <el-tab-pane label="公告管理" name="notice">
        <div class="toolbar">
          <el-input v-model="noticeQuery.keyword" placeholder="搜索公告标题或正文" clearable style="width: 240px"
                    @keyup.enter="searchNotices" />
          <el-button type="primary" @click="searchNotices">查询</el-button>
          <el-button @click="resetNoticeQuery">重置</el-button>
          <el-button type="success" class="create-btn" @click="openCreateNotice">+ 发布公告</el-button>
        </div>

        <el-table :data="noticeList" v-loading="noticeLoading" border stripe size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="发布时间" width="160" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="$router.push(`/post/${row.id}`)">查看</el-button>
              <el-button link type="danger" size="small" @click="onDeleteNotice(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="noticeTotal" :current-page="noticeQuery.page" :page-size="noticeQuery.size"
                       @current-change="(p) => { noticeQuery.page = p; loadNotices() }" />
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

      <!-- ==================== 限量徽章活动管理 ==================== -->
      <el-tab-pane label="活动管理" name="activity">
        <div class="toolbar">
          <el-input v-model="actQuery.keyword" placeholder="搜索徽章称号或标题" clearable style="width: 240px"
                    @keyup.enter="searchActivities" />
          <el-select v-model="actQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
          <el-button type="primary" @click="searchActivities">查询</el-button>
          <el-button @click="resetActQuery">重置</el-button>
          <el-button type="success" class="create-btn" @click="openCreateActivity">+ 发布限量徽章</el-button>
        </div>

        <el-table :data="actList" v-loading="actLoading" border stripe size="small">
          <el-table-column label="徽章称号" width="150">
            <template #default="{ row }">
              <BadgePill v-if="row.badgeName" :name="row.badgeName" />
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="活动标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="发放进度" width="130" align="center">
            <template #default="{ row }">
              <span class="progress-num">{{ row.awardedCount || 0 }}</span>
              <span class="muted"> / {{ row.stock }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="80" align="right">
            <template #default="{ row }">
              <b :class="row.remainStock > 0 ? 'remain-ok' : 'remain-none'">{{ row.remainStock ?? row.stock }}</b>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="ACTIVITY_STATUS[row.status]?.type || 'info'" size="small">
                {{ ACTIVITY_STATUS[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="起止时间" width="200">
            <template #default="{ row }">
              <div class="time-cell">{{ row.beginTime }}</div>
              <div class="time-cell">{{ row.endTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="215" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditActivity(row)">编辑</el-button>
              <el-button v-if="row.status === 1" link type="warning" size="small" @click="onEndActivity(row)">提前结束</el-button>
              <el-button link type="success" size="small" @click="$router.push(`/activity/${row.id}`)">前台查看</el-button>
              <el-button link type="danger" size="small" @click="onDeleteActivity(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="actTotal" :current-page="actQuery.page" :page-size="actQuery.size"
                       @current-change="(p) => { actQuery.page = p; loadActivities() }" />
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

    <!-- 发布 / 编辑限量徽章 -->
    <el-dialog v-model="actVisible" :title="actEditingId ? '编辑限量徽章' : '发布限量徽章'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="徽章称号" required>
          <!-- filterable + allow-create：可选常用称号，也能直接输入新称号 -->
          <el-select
            v-model="actForm.badgeName"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入称号（如：凤川祥）"
            style="width: 100%"
          >
            <el-option v-for="b in PRESET_BADGES" :key="b" :label="b" :value="b" />
          </el-select>
          <p class="field-tip">称号全站唯一，同名活动只能有一个；输入新称号后回车即可创建。</p>
        </el-form-item>

        <el-form-item label="发放数量" required>
          <el-input-number v-model="actForm.stock" :min="1" :max="100000" style="width: 180px" />
          <span class="field-tip inline">限量份数，抢完即止</span>
        </el-form-item>

        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="actForm.beginTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="actForm.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="奖励说明">
          <el-input v-model="actForm.awardDesc" maxlength="255" show-word-limit
                    placeholder="可选，留空则自动生成「限量 N 枚，先到先得」" />
        </el-form-item>
      </el-form>

      <el-alert
        v-if="actEditingId"
        type="info"
        :closable="false"
        show-icon
        title="调整发放数量不会影响已抢到的份数：已抢 5 份、总量改为 8，则剩余可抢为 3。"
      />

      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button type="primary" :loading="actSaving" @click="submitActivity">
          {{ actEditingId ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 发布公告：公告就是一条不挂吧、恒定置顶的帖子 -->
    <el-dialog v-model="noticeVisible" title="发布公告" width="560px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="noticeForm.title" maxlength="64" show-word-limit placeholder="如：关于社区规范的公告" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="noticeForm.content" type="textarea" :rows="6" maxlength="50000"
                    placeholder="公告正文" />
        </el-form-item>
      </el-form>
      <p class="field-tip">
        公告发布后会立即置顶在首页列表最前，所有人都能看到；点击可进入详情页正常回复。
      </p>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" :loading="noticeSaving" @click="submitNotice">发布</el-button>
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
.muted { color: #bbb; }
.progress-num { color: #f56c6c; font-weight: bold; font-size: 14px; }
.remain-ok { color: #67c23a; }
.remain-none { color: #c0c4cc; }
.time-cell { font-size: 11px; color: #999; line-height: 1.5; }
.field-tip { font-size: 11px; color: #bbb; line-height: 1.6; }
.field-tip.inline { margin-left: 10px; }
</style>
