--[[
  原子扣减库存（秒杀防超卖）——独立脚本文件版，对照 RedisPlaygroundController 里的内嵌写法

  调用方：RedisLuaScriptController#deductStock
  入参约定：
    KEYS[1] = 库存 key，例如 demo:stock:p1     ← 内存 key 必须放 KEYS，不能写死在脚本里
    ARGV[1] = 本次扣减数量，例如 "1"           ← 业务参数放 ARGV
  返回值：
    >= 0  扣减成功，值为扣减后的剩余库存
    -1    库存未初始化（key 不存在）
    -2    库存不足，扣减失败

  为什么必须用 Lua：
    "读取 → 判断 → 扣减" 三步如果分三次往返 Redis，
    并发的两个请求可能都读到 stock=1 然后各自扣减 → 超卖成 -1。
    Lua 脚本在 Redis 内作为一个命令原子执行，中间不会插入其他命令。
]]

-- 局部变量：Lua 里不加 local 就是全局变量（会污染脚本环境，必须加）
local stockKey = KEYS[1]

-- tonumber：ARGV 传进来都是字符串，参与数值比较前必须转换
local deductQuantity = tonumber(ARGV[1])

-- redis.call 执行 Redis 命令；key 不存在时 GET 返回 false
local currentStock = tonumber(redis.call('GET', stockKey))

-- 库存未初始化
if currentStock == nil then
    return -1
end

-- 库存不足（注意 Lua 里用 ~= 表示"不等于"，== 是相等）
if currentStock < deductQuantity then
    return -2
end

-- DECRBY 原子递减并返回新值；直接 return 它，Lua number 会映射成 Redis integer
return redis.call('DECRBY', stockKey, deductQuantity)
