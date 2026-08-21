# Java 高性能短链系统

基于 Spring Boot 3 + WebFlux + JDK 21 实现的短链系统，完整落地了 `docs/design.md` 中的核心设计：
**Leaf 号段发号器、Caffeine + Redis + MySQL 三级缓存、Disruptor 异步统计、ShardingSphere 分库分表、Sa-Token 登录鉴权**。

## 一、架构总览

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

## 二、模块结构

```
shortLink
├── short-link-common     通用层：Base62、URL 校验、Redis Key 规划、错误码、Result、DTO
├── short-link-core       核心层：发号器、三级缓存、布隆过滤器、Disruptor 统计、领域服务、DAL
├── short-link-server     接入层：WebFlux 控制器、Sa-Token 鉴权、限流、OpenAPI 文档（可执行 jar）
└── deploy
    ├── docker-compose.yml   一键启动 MySQL 8 + Redis 7
    └── sql/01-init.sql      建库建表脚本（主库 + 4 分片库 × 16 表 = 64 分片）
```

## 三、快速开始

### 1. 启动基础设施

```bash
cd deploy
docker compose up -d          # MySQL(3306) + Redis(6379)，首次启动自动建库建表
```

### 2. 构建与启动应用

```bash
# JDK 21+
mvn clean package -DskipTests
java -jar short-link-server/target/short-link-server-1.0.0-SNAPSHOT.jar
```

启动成功后：

- 应用：<http://localhost:8080>
- Swagger 文档：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>
- 默认管理员：`admin / admin123`（仅用户表为空时创建，登录后请立即修改）

> 连接密码等在 `short-link-server/src/main/resources/sharding.yaml`（数据库）与
> `application.yml`（Redis）中修改。

## 四、API 一览

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/auth/register` | 注册 | 公开 |
| POST | `/api/auth/login` | 登录，返回 token | 公开 |
| POST | `/api/auth/logout` | 退出 | 登录 |
| GET | `/api/auth/me` | 当前用户信息 | 登录 |
| POST | `/api/short-links` | 创建短链（可指定分组、域名、有效期） | 登录 |
| GET | `/api/short-links?groupId=` | 我的短链分页（可按分组过滤，0=未分组） | 登录 |
| GET | `/api/short-links/{code}` | 详情（含实时统计） | 属主/管理员 |
| PUT | `/api/short-links/{code}/group` | 移动到分组（0=未分组） | 属主/管理员 |
| PUT | `/api/short-links/{code}/status` | 上线/下线（缓存失效，秒级生效） | 属主/管理员 |
| DELETE | `/api/short-links/{code}` | 删除（移入回收站，默认保留 30 天） | 属主/管理员 |
| POST | `/api/groups` | 创建分组 | 登录 |
| GET | `/api/groups` | 分组列表（含各分组短链数） | 登录 |
| PUT | `/api/groups/{id}` | 重命名分组 | 登录 |
| DELETE | `/api/groups/{id}` | 删除分组（组内短链移回未分组） | 登录 |
| GET | `/api/recycle-bin` | 回收站分页（含自动清除时间） | 登录 |
| PUT | `/api/recycle-bin/{code}/restore` | 从回收站还原 | 属主/管理员 |
| DELETE | `/api/recycle-bin/{code}` | 彻底删除（物理删除，不可恢复） | 属主/管理员 |
| GET | `/api/domains` | 可用域名列表（仅启用，默认域名排前） | 登录 |
| GET | `/api/stats/{code}` | 实时统计：当日 PV/UV、累计 PV | 属主/管理员 |
| GET | `/api/stats/{code}/history?days=7` | 按日归档历史统计 | 属主/管理员 |
| GET | `/api/admin/short-links` | 全部短链分页（可按状态过滤，2=回收站） | ADMIN |
| PUT | `/api/admin/short-links/{code}/status` | 强制上下线任意短链 | ADMIN |
| DELETE | `/api/admin/short-links/{code}` | 删除任意短链（入回收站） | ADMIN |
| POST | `/api/admin/recycle-bin/purge` | 手动触发回收站清理（返回清理条数） | ADMIN |
| GET/POST | `/api/admin/domains` | 域名列表 / 新增域名 | ADMIN |
| PUT | `/api/admin/domains/{id}/status` | 上线/下线域名（默认域名不可下线） | ADMIN |
| PUT | `/api/admin/domains/{id}/default` | 设为默认域名 | ADMIN |
| DELETE | `/api/admin/domains/{id}` | 删除域名（默认域名不可删除） | ADMIN |
| GET | `/{code}` | 短码跳转（302 + 异步统计） | 公开 |

登录后 token 放在请求头 `shortLinkToken: {tokenValue}`。

### 分组、回收站与域名

- **分组**：短链创建时可指定 `groupId`，可随时移动；分组列表附带各组短链数；
  删除分组时组内短链自动移回“未分组”。
- **回收站**：删除的短链进入回收站（状态 2，记录 `delete_time`），可还原或彻底删除；
  超过保留天数（`shortlink.recycle-bin.retention-days`，默认 30 天）的短链由每日
  定时任务（`purge-cron`，默认 03:30）物理清除，并同步清理缓存键、累计 PV 与统计归档行。
- **域名**：管理端维护短链域名池（含协议前缀，校验合法性、全局唯一），默认域名全局唯一且
  不可删除/停用；创建短链时用 `domainId` 指定域名，返回的 `shortUrl` 使用对应域名。
  跳转解析与域名无关，任意已配置域名下的短码均可正常 302。

## 五、核心设计说明

### 5.1 发号器（Leaf-Segment 双 Buffer）

`core/id/SegmentIdGenerator`：号段窗口 `[min, max]` 取号为纯内存 CAS；
剩余量低于 20% 时由独立线程异步预加载下一段；号段耗尽且下一段未就绪时
同步阻塞等待（默认 5s 超时）。DB 交互频率 ≈ step/QPS，趋近于零。

### 5.2 短码与路由

ID → 7 位 Base62（起始 10 亿防枚举）。ShardingSphere 按 `short_code`
哈希取模路由：库 = `hash(code) % 4`，表 = `hash(code) % 16`，共 64 分片。
> 注：ShardingSphere 5.5 的 `HASH_MOD` 属于"自动分片算法"，标准表策略需用
> INLINE 表达式实现同语义（见 `sharding.yaml`）。

### 5.3 三级缓存读路径

`core/cache/ThreeLevelShortUrlCache` + `core/service/ShortLinkService#resolve`：

1. **L1** Caffeine（默认 5 万条 / 30s TTL），Optional.empty 表示空值缓存
2. **布隆过滤器**拦截不存在的短码（未就绪/重建期间 fail-open 保证正确性）
3. **L2** Redis（Kryo5 编码，24h TTL ± 10% 随机抖动防雪崩）
4. **L3** MySQL 回源并回填 L1/L2；未命中写短 TTL 空值防穿透

### 5.4 轻量统计（Disruptor + Redis Pipeline）

跳转线程仅向 RingBuffer（65536）发布事件；消费线程攒批
（256 条或 200ms）后通过一次 Pipeline 写入：

- `s:pv:{code}:{yyyyMMdd}` 当日 PV（48h TTL）
- `s:uv:{code}:{yyyyMMdd}` 当日 UV，HyperLogLog 去重（IP+UA 指纹）
- `s:pv:t:{code}` 累计 PV（永不过期）
- `s:codes:{yyyyMMdd}` 当日活跃短码集合（归档反查用，49h TTL）

每小时 `@Scheduled` 将当日+昨日数据幂等 upsert 至 `short_url_stats` 表。

### 5.5 鉴权与安全

- Sa-Token（reactor 版）+ 自实现 `RedissonSaTokenDao`：登录态存 Redis，多实例共享
- 路由级拦截：`/api/**` 需登录，`/api/admin/**` 需 ADMIN 角色
- Sa-Token 1.39 WebFlux 为 ThreadLocal 上下文模型，控制器内 StpUtil 调用
  统一经 `SaTokenReactorContext` 绑定 exchange
- 密码 BCrypt 加密；长链接仅允许 http/https 且校验域名黑名单
- 按网关语义实现 `RateLimitWebFilter`：Redisson 令牌桶，默认单 IP 100 QPS

## 六、单元测试

```bash
mvn test
```

覆盖 Base62 编解码往返、URL 合法性/黑名单、发号器单调唯一性/并发唯一性/跨号段。

## 七、日志配置

`short-link-server/src/main/resources/logback-spring.xml`：

- **控制台**：Spring Boot 彩色格式，供 `docker logs` / `kubectl logs` 采集
- **主日志** `logs/short-link-server.log`：INFO 及以上，按天 + 100MB 滚动，
  gzip 压缩，保留 30 天 / 总量 2GB
- **错误日志** `logs/short-link-server-error.log`：仅 ERROR，独立滚动，便于告警检索
- **异步写入**：文件输出经 AsyncAppender，`neverBlock=true`，队列满丢弃日志
  而不阻塞跳转热路径
- **级别**：dev profile（默认）下 `com.shortlink` 为 DEBUG（含 MyBatis SQL）；
  生产以 `SPRING_PROFILES_ACTIVE=prod` 启动时为 INFO；ShardingSphere/Hikari/
  Redisson 等三方组件统一降噪至 WARN
- **目录**：默认 `./logs`，可用环境变量 `LOG_HOME` 覆盖（如挂载数据卷）

## 八、部署形态与后续规划

当前为**模块化单体**（一个可执行 jar），生产可按设计文档演进：

- 独立部署 Spring Cloud Gateway 承接限流/鉴权（应用内已内置等价能力）
- Redis Cluster：调整 `RedissonConfiguration` 为 `useClusterServers` 即可
- 幂等查重、防枚举混淆乘数、Nginx Lua 热点短路、K8s 编排（见 design.md 第七~十章）
