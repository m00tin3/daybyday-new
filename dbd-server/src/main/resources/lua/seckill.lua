-- ============================================================
-- 秒杀原子预扣脚本（Redis 单线程执行整个脚本，天然原子）
-- 一次 Redis 往返完成：库存判断 + 库存预扣 + 一人一单标记
-- ============================================================
-- KEYS[1] = dbd:seckill:stock:{activityId}         剩余库存
-- KEYS[2] = dbd:seckill:order:{activityId}:{userId}  一人一单标记
-- ARGV[1] = userId
-- 返回：0 成功；1 库存不足（含未初始化）；2 已抢过（一人一单）
-- ============================================================

-- 库存未初始化（活动未预热）视为不可抢
if redis.call('exists', KEYS[1]) == 0 then
  return 1
end

local stock = tonumber(redis.call('get', KEYS[1]))
if stock <= 0 then
  return 1
end

-- SETNX 原子标记一人一单：已存在（返回 0）则重复抢
if redis.call('setnx', KEYS[2], ARGV[1]) == 0 then
  return 2
end

-- 库存预扣
redis.call('decr', KEYS[1])
return 0
