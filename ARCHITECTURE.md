# 核心设计与架构

落地 `docs/design.md` 的关键设计。模块划分见 README「模块结构」。

## 1. 请求链路总览

```
用户请求
   │
   ▼
WebFlux (Netty)  ──  Sa-Token 鉴权过滤器(SaReactorFilter)  ──  按IP令牌桶限流(RateLimitWebFilter)
   │
   ├── 写路径: URL校验/黑名单 → 号段发号器(纯内存CAS) → Base62 → INSERT 分片表 → 布隆过滤器+缓存预热
   │
   └── 读路径(跳转): L1 Caffeine → 布隆过滤器(防穿透) → L2 Redis(Kryo) → L3 MySQL(ShardingSphere)
                     → Disruptor RingBuffer 发布点击事件(纳秒级) → 302
   │
   ├── Disruptor 消费线程: 攒批(256条/200ms) → Redis Pipeline(PV/UV/累计PV, 一次RTT)
   │
   └── @Scheduled 每小时: Redis 当日/昨日数据 → upsert MySQL short_url_stats(幂等归档)
```

阻塞 JDBC 经 `Reactors.call` → boundedElastic 包装，不阻塞 Netty 事件循环。

## 2. 发号器（Leaf-Segment 双 Buffer）

`core/id/SegmentIdGenerator`：号段窗口 `[min, max]` 取号为纯内存 CAS；
剩余量低于 20% 时由独立线程异步预加载下一段；号段耗尽且下一段未就绪时
同步阻塞等待（默认 5s 超时）。DB 交互频率 ≈ step/QPS，趋近于零。

## 3. 短码与分片

ID → 7 位 Base62（起始 10 亿防枚举）。ShardingSphere 按 `short_code`
哈希取模路由：库 = `hash(code) % 4`，表 = `hash(code) % 16`，共 64 分片。
> 注：ShardingSphere 5.5 的 `HASH_MOD` 属于“自动分片算法”，标准表策略需用
> INLINE 表达式实现同语义（见 `sharding.yaml`）。

## 4. 三级缓存读路径

`core/cache/ThreeLevelShortUrlCache` + `core/service/ShortLinkService#resolve`：

1. **L1** Caffeine（默认 5 万条 / 30s TTL），Optional.empty 表示空值缓存
2. **布隆过滤器**拦截不存在的短码（未就绪/重建期间 fail-open 保证正确性）
3. **L2** Redis（Kryo5 编码，24h TTL ± 10% 随机抖动防雪崩）
4. **L3** MySQL 回源并回填 L1/L2；未命中写短 TTL 空值防穿透

## 5. 轻量统计（Disruptor + Redis Pipeline）

跳转线程仅向 RingBuffer（65536）发布事件；消费线程攒批
（256 条或 200ms）后通过一次 Pipeline 写入：

- `s:pv:{code}:{yyyyMMdd}` 当日 PV（48h TTL）
- `s:uv:{code}:{yyyyMMdd}` 当日 UV，HyperLogLog 去重（IP+UA 指纹）
- `s:pv:t:{code}` 累计 PV（永不过期）
- `s:codes:{yyyyMMdd}` 当日活跃短码集合（归档反查用，49h TTL）

每小时 `@Scheduled` 将当日+昨日数据幂等 upsert 至 `short_url_stats` 表。
列表页统计走批量查询（一次 RBatch 往返 + 一条 GROUP BY）。

## 6. 鉴权与安全

- Sa-Token（reactor 版）+ 自实现 `RedissonSaTokenDao`：登录态存 Redis，多实例共享
- 路由级拦截：`/api/**` 需登录，`/api/admin/**` 需 ADMIN 角色
- Sa-Token 1.39 WebFlux 为 ThreadLocal 上下文模型，控制器内 StpUtil 调用
  统一经 `SaTokenReactorContext` 绑定 exchange
- 密码 BCrypt 加密；长链接仅允许 http/https 且校验域名黑名单
- 按网关语义实现 `RateLimitWebFilter`：Redisson 令牌桶，默认单 IP 100 QPS
- Jackson 全局 Long→String 序列化，防前端 53 位精度丢失

## 7. 部署形态与后续规划

当前为**模块化单体**（一个可执行 jar，前端内嵌），生产可按设计文档演进：

- 独立部署 Spring Cloud Gateway 承接限流/鉴权（应用内已内置等价能力）
- Redis Cluster：调整 `RedissonConfiguration` 为 `useClusterServers` 即可
- 幂等查重、防枚举混淆乘数、Nginx Lua 热点短路、K8s 编排（见 design.md 第七~十章）
