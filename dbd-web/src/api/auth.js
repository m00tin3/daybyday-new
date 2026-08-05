// 认证模块接口（对应 API.md §3.1）
import request from '../utils/request'

/** 发送短信验证码 POST /api/auth/code */
export function sendCode(phone) {
  return request.post('/auth/code', { phone })
}

/** 验证码登录（未注册自动注册）POST /api/auth/login → { token, userInfo } */
export function loginByCode(phone, code) {
  return request.post('/auth/login', { phone, code })
}

/** 显式注册 POST /api/auth/register */
export function register(data) {
  return request.post('/auth/register', data)
}

/** 当前登录用户信息 🔒 GET /api/auth/me */
export function getUserInfo() {
  return request.get('/auth/me')
}
