<script setup>
// 登录页：账号 + 验证码（对应 API.md §3.1）
// 登录成功后写入 Pinia（token 持久化 localStorage），按 redirect 参数回跳来源页
//
// 账号字段兼容两类：
//   普通用户 → 11 位手机号（如 13800000001）
//   管理员   → 2485617328（10 位标识，手机号规则之外，靠白名单放行）
// 规则统一在 utils/validate.js，发送验证码与登录两处入口都要过。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendCode, loginByCode } from '../api/auth'
import { useUserStore } from '../stores/user'
import { isValidAccount, ACCOUNT_ERROR_MSG } from '../utils/validate'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const phone = ref('')
const code = ref('')
const sending = ref(false) // 发送验证码请求中
const loginLoading = ref(false)
const countdown = ref(0) // 验证码 60s 倒计时
const phoneError = ref('') // 手机号格式错误提示（行内常驻显示，比一闪而过的 toast 更明确）

/**
 * 校验手机号并写回行内错误提示。
 * 发送验证码与登录两个入口共用，保证两边提示一致。
 * @returns {boolean} 是否通过
 */
function checkPhone() {
  const v = phone.value.trim()
  if (!v) {
    phoneError.value = '请输入手机号'
    return false
  }
  if (!isValidAccount(v)) {
    phoneError.value = ACCOUNT_ERROR_MSG
    return false
  }
  phoneError.value = ''
  return true
}

// 发送验证码：账号格式校验 + 60s 重发限制
async function onSendCode() {
  // 格式不合法直接拦在这里，不发短信
  if (!checkPhone()) return
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
  // 账号格式必须先于验证码判空：直接点登录时，不合法要给"手机号不合法"，
  // 而不是发一个注定被后端拒掉的请求
  if (!checkPhone()) return
  if (!code.value) {
    ElMessage.warning('请输入验证码')
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
          <el-input
            v-model="phone"
            placeholder="账号（手机号 / 管理员标识）"
            maxlength="20"
            size="large"
            @input="phoneError = ''"
          />
          <p v-if="phoneError" class="field-error">{{ phoneError }}</p>
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
      <p class="demo-tip">
        演示账号：13800000001 ~ 13800000006（验证码 123456）
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.login-page { display: flex; justify-content: center; padding-top: 80px; }
.login-card { width: 380px; padding: 20px; }
.title { text-align: center; margin-bottom: 24px; color: #4e6ef2; }
.code-row { display: flex; gap: 8px; width: 100%; }
.submit { width: 100%; }
.demo-tip { margin-top: 14px; font-size: 12px; color: #aaa; text-align: center; }
/* 手机号格式错误：行内常驻提示，输入时由 @input 清掉 */
.field-error { width: 100%; margin: 4px 0 0; font-size: 12px; line-height: 1.4; color: var(--el-color-danger); }
</style>
