<script setup>
// 个人资料页：展示 + 修改基础信息（对应 API.md §3.4）
// 数据来源：
//   getUserInfo() → /api/auth/me   取本人完整资料（含手机号，仅本人可见）
//   getUserProfile(id) → /api/user/{id}  取发帖/粉丝/关注统计
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo } from '../api/auth'
import { getUserProfile, updateUserProfile } from '../api/user'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const stats = ref({ postCount: 0, followerCount: 0, followingCount: 0 })

const form = reactive({
  nickname: '',
  signText: '',
  icon: '',
  phone: ''
})

const formRef = ref(null)
const rules = {
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { min: 1, max: 32, message: '昵称长度需在 1-32 字之间', trigger: 'blur' }
  ],
  signText: [{ max: 128, message: '个性签名不能超过 128 字', trigger: 'blur' }],
  icon: [{ max: 255, message: '头像 URL 不能超过 255 字符', trigger: 'blur' }],
  phone: [{ pattern: /^\d{6,20}$/, message: '账号需为 6-20 位数字', trigger: 'blur' }]
}

/** 头像：填的是图片地址就渲染图片，否则按文本（emoji 等）展示 */
const isUrl = (v) => typeof v === 'string' && /^https?:\/\//i.test(v)
const previewAvatar = computed(() => form.icon)

async function load() {
  loading.value = true
  try {
    const res = await getUserInfo()
    const u = res.data ?? {}
    form.nickname = u.nickname ?? ''
    form.signText = u.signText ?? ''
    form.icon = u.icon ?? ''
    form.phone = u.phone ?? ''
    // 统计信息来自用户主页接口
    if (u.id) {
      try {
        const p = await getUserProfile(u.id)
        stats.value = {
          postCount: p.data?.postCount ?? 0,
          followerCount: p.data?.followerCount ?? 0,
          followingCount: p.data?.followingCount ?? 0
        }
      } catch { /* 统计失败不影响资料展示 */ }
    }
  } catch {
    // 401 会被 request.js 拦截并跳登录
  } finally {
    loading.value = false
  }
}

function cancelEdit() {
  editing.value = false
  load() // 还原为服务端最新值
}

async function save() {
  try {
    await formRef.value.validate()
  } catch { return }
  saving.value = true
  try {
    const res = await updateUserProfile({
      nickname: form.nickname,
      signText: form.signText,
      icon: form.icon,
      phone: form.phone
    })
    // 同步本地用户态，避免顶栏昵称与资料页不一致
    userStore.setUserInfo(res.data)
    ElMessage.success('资料已更新')
    editing.value = false
    load()
  } catch { /* 拦截器已提示（如账号被占用） */ }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div class="profile-page" v-loading="loading">
    <!-- 资料展示卡 -->
    <div class="card">
      <div class="head">
        <div class="avatar">
          <img v-if="isUrl(form.icon)" :src="form.icon" alt="头像" />
          <span v-else>{{ form.icon || '👤' }}</span>
        </div>
        <div class="meta">
          <h2>{{ form.nickname || '未设置昵称' }}</h2>
          <p class="sign">{{ form.signText || '这个人很懒，什么都没写' }}</p>
          <p class="sub">
            登录账号：{{ form.phone || '—' }}
            <el-tag v-if="userStore.isAdmin" type="danger" size="small" class="role-tag">管理员</el-tag>
          </p>
          <p class="sub">发帖 {{ stats.postCount }} · 粉丝 {{ stats.followerCount }} · 关注 {{ stats.followingCount }}</p>
        </div>
        <div class="actions">
          <el-button type="primary" round @click="editing = !editing">
            {{ editing ? '收起编辑' : '编辑资料' }}
          </el-button>
          <el-button round @click="router.push(`/user/${userStore.userInfo?.id}`)">我的主页</el-button>
        </div>
      </div>
    </div>

    <!-- 编辑表单 -->
    <div v-if="editing" class="card">
      <h3 class="form-title">编辑资料</h3>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="edit-form">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" show-word-limit placeholder="展示给其他用户的名字" />
        </el-form-item>
        <el-form-item label="个性签名" prop="signText">
          <el-input v-model="form.signText" type="textarea" :rows="3" maxlength="128" show-word-limit
                    placeholder="一句话介绍自己" />
        </el-form-item>
        <el-form-item label="头像" prop="icon">
          <div class="avatar-row">
            <el-input v-model="form.icon" maxlength="255" placeholder="图片地址（http/https），留空则显示默认图标" />
            <div class="avatar-preview">
              <img v-if="isUrl(previewAvatar)" :src="previewAvatar" alt="预览" />
              <span v-else>{{ previewAvatar || '👤' }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="登录账号" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="6-20 位数字，修改后请用新账号登录" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="cancelEdit">取消</el-button>
        </el-form-item>
      </el-form>
      <p class="note">
        注：头像当前仅支持填写图片地址（项目暂无文件上传与对象存储）。
        登录账号是登录凭证，修改后需使用新账号登录。
      </p>
    </div>
  </div>
</template>

<style scoped>
.profile-page { max-width: 880px; margin: 16px auto; }
.card { background: #fff; border-radius: 6px; padding: 20px; margin-bottom: 16px; }
.head { display: flex; align-items: center; gap: 16px; }
.avatar { width: 72px; height: 72px; border-radius: 50%; background: #4e6ef2; color: #fff; font-size: 32px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.meta { flex: 1; }
.meta h2 { margin-bottom: 6px; }
.sign { color: #999; font-size: 13px; margin-bottom: 6px; }
.sub { color: #aaa; font-size: 12px; margin-bottom: 2px; }
.role-tag { margin-left: 6px; }
.actions { display: flex; gap: 8px; }
.form-title { font-size: 15px; margin-bottom: 16px; }
.edit-form { max-width: 560px; }
.avatar-row { display: flex; gap: 12px; align-items: center; width: 100%; }
.avatar-preview { width: 44px; height: 44px; border-radius: 50%; background: #f0f2f5; display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; font-size: 20px; }
.avatar-preview img { width: 100%; height: 100%; object-fit: cover; }
.note { font-size: 12px; color: #aaa; line-height: 1.7; }
</style>
