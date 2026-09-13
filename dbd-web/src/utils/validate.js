/**
 * 账号格式的唯一权威定义。
 *
 * 登录页与个人资料页都用到，之前两处各写了一份 `^\d{6,20}$`，改规则要改两遍。
 *
 * 为什么会存在"白名单"这种东西：
 *   普通用户用 11 位手机号登录（后端 AuthServiceImpl.PHONE_PATTERN = ^1\d{10}$ 管注册），
 *   但管理员账号是 2485617328 —— 10 位，故意不是手机号（它本来就是"管理员标识"而非手机号，
 *   见 README 演示账号表）。前端若一律按 11 位手机号卡，管理员就发不出验证码、
 *   而线上 ADMIN_FREE_LOGIN 已是 false，等于把管理员锁在门外。
 *
 * 注意后端刻意保持宽松：AuthServiceImpl.ACCOUNT_PATTERN = ^\d{6,20}$ 仍用于发送验证码。
 * 前端收紧只是体验优化（少发一次必然失败的请求），不是安全边界。
 */

/** 管理员标识（非手机号，白名单放行） */
export const ADMIN_ACCOUNT = '2485617328'

/** 中国大陆手机号：11 位、以 1 开头 */
export const PHONE_RE = /^1\d{10}$/

/**
 * 账号是否合法：11 位手机号，或管理员标识。
 * @param {string} value
 * @returns {boolean}
 */
export function isValidAccount(value) {
  const v = (value ?? '').trim()
  return PHONE_RE.test(v) || v === ADMIN_ACCOUNT
}

/** 不合法的统一提示语，保证两处入口文案一致 */
export const ACCOUNT_ERROR_MSG = '手机号不合法'
