<script setup>
// 登录页：手机号 + 验证码（对应 API.md §3.1）
// 登录成功后写入 Pinia（token 持久化 localStorage），按 redirect 参数回跳来源页
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendCode, loginByCode } from '../api/auth'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const phone = ref('')
const code = ref('')
const sending = ref(false) // 发送验证码请求中
const loginLoading = ref(false)
const countdown = ref(0) // 验证码 60s 倒计时

// 发送验证码：手机号格式校验 + 60s 重发限制
async function onSendCode() {
  if (!/^1\d{10}$/.test(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await sendCode(phone.value)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally {
    sending.value = false
  }
}

// 登录：成功后写入用户态，回跳来源页
async function onLogin() {
  if (!phone.value || !code.value) {
    ElMessage.warning('请输入手机号和验证码')
    return
  }
  loginLoading.value = true
  try {
    const res = await loginByCode(phone.value, code.value)
    userStore.setLogin(res.data)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } finally {
    loginLoading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">DbD · 登录</h2>
      <el-form @submit.prevent="onLogin">
        <el-form-item>
          <el-input v-model="phone" placeholder="手机号" maxlength="11" size="large" />
        </el-form-item>
        <el-form-item>
          <div class="code-row">
            <el-input v-model="code" placeholder="验证码" maxlength="6" size="large" />
            <el-button size="large" :disabled="countdown > 0" :loading="sending" @click="onSendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loginLoading" @click="onLogin">
          登录 / 注册
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page { display: flex; justify-content: center; padding-top: 80px; }
.login-card { width: 380px; padding: 20px; }
.title { text-align: center; margin-bottom: 24px; color: #4e6ef2; }
.code-row { display: flex; gap: 8px; width: 100%; }
.submit { width: 100%; }
</style>
