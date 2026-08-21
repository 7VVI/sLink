# Java 高性能短链系统 — 轻量化整体设计

------

## 一、需求与量级假设

| 指标       | 数值                             |
| ---------- | -------------------------------- |
| 读写比     | 100 : 1                          |
| 写入 QPS   | 1,000（峰值 5,000）              |
| 读取 QPS   | 100,000（峰值 50 万）            |
| 跳转 P99   | < 50ms                           |
| 日新增短链 | ~1 亿条                          |
| 数据保留   | 5 年                             |
| 可用性     | 99.99%                           |
| 统计需求   | PV / UV / 累计访问量（轻量实时） |

短码长度：62 进制 × 7 位 ≈ 3.5 万亿空间，充足。

------

### **短码生成策略（关键选型）**

| 自增 ID + Base62 | 全局发号器生成 ID，转 62 进制 | **无冲突、有序、可预估** | 依赖发号器 |
| ---------------- | ----------------------------- | ------------------------ | ---------- |

## 二、技术选型

| 层级       | 选型                                | 理由                        |
| ---------- | ----------------------------------- | --------------------------- |
| 应用框架   | Spring Boot 3.x + **WebFlux**+JDK21 | 响应式非阻塞，线程开销极小  |
| 接入网关   | Spring Cloud Gateway                | 限流、鉴权、路由            |
| 发号器     | **Leaf-Segment 号段模式**           | 纯内存取号，DB 压力趋近于零 |
| 本地缓存   | Caffeine                            | 纳秒级热点读取              |
| 分布式缓存 | Redisson（Redis Cluster）           | 缓存 + 布隆过滤器 + 统计    |
| 持久化     | MySQL 8.0 + ShardingSphere-JDBC     | 分库分表透明路由            |
| 统计方案   | **Disruptor + Redis Pipeline**      | 零中间件，毫秒级实时        |
| 统计归档   | Spring @Scheduled → MySQL           | 每小时归档，支持历史查询    |
| 序列化     | Kryo5（Redis）/ JSON（API）         | 性能与可读性平衡            |


✅ 全部统计与监控靠 **Redis + MySQL + Spring Actuator** 完成

------

## 三、整体架构

```
                         用户请求
                            │
                     ┌──────▼──────┐
                     │  Nginx / LB │  (L4负载均衡 + Lua热点短路)
                     └──────┬──────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
        ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐
        │ Gateway×N │ │Gateway×N│ │ Gateway×N │  限流/鉴权/路由
        └─────┬─────┘ └────┬────┘ └─────┬─────┘
              └─────────────┼─────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │         短链服务集群 (无状态)           │
        │                                       │
        │  ┌─────────────┐   ┌───────────────┐  │
        │  │  写服务 ×M   │   │  读服务 ×N    │  │
        │  │  生成短链    │   │  跳转解析     │  │
        │  └──────┬──────┘   └───┬───────┬───┘  │
        │         │              │       │      │
        │         │              │  ┌────▼────┐ │
        │         │              │  │Disruptor│ │
        │         │              │  │→Pipeline│ │  ← 异步统计
        │         │              │  └────┬────┘ │
        └─────────┼──────────────┼───────┼──────┘
                  │              │       │
           ┌──────▼──────┐      │       │
           │ 发号器(Leaf) │      │       │
           │ 号段双Buffer │      │       │
           └──────┬──────┘      │       │
                  │              │       │
                  ▼              ▼       ▼
        ┌─────────────────────────────────────┐
        │           Redis Cluster             │
        │  · 短链缓存 (surl:{code})           │
        │  · 布隆过滤器 (防穿透)              │
        │  · PV统计 (INCR)                   │
        │  · UV统计 (HyperLogLog)            │
        └──────────────┬──────────────────────┘
                       │ miss / 定时归档
                       ▼
        ┌─────────────────────────────────────┐
        │     MySQL (ShardingSphere 分片)      │
        │  · short_url_{0..255}  主表         │
        │  · short_url_stats     统计归档表    │
        │  · leaf_alloc          发号器表      │
        └─────────────────────────────────────┘
```

**组件总数：Nginx + Gateway + 应用服务 + Redis Cluster + MySQL，仅此而已。**

------

## 四、核心模块详细设计

### 4.1 发号器（Leaf-Segment 号段模式）

```sql
CREATE TABLE leaf_alloc (
    biz_tag     VARCHAR(128) PRIMARY KEY,
    max_id      BIGINT NOT NULL DEFAULT 1000000000,  -- 从10亿起步
    step        INT NOT NULL DEFAULT 100000,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
@Component
public class SegmentIdGenerator {

    private volatile Segment current;
    private volatile Segment next;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final JdbcTemplate jdbc;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    public long nextId() {
        long id = current.getIdle().incrementAndGet();
        // 消耗到80%时异步预加载下一段
        if (current.remaining() < current.total() / 5) {
            asyncLoadNext();
        }
        if (id > current.getMax()) {
            // 极端情况：当前段耗尽，同步等待next就绪
            synchronized (this) {
                while (next == null) Thread.onSpinWait();
                current = next;
                next = null;
            }
            id = current.getIdle().incrementAndGet();
        }
        return id;
    }

    private void asyncLoadNext() {
        if (loading.compareAndSet(false, true)) {
            loader.submit(() -> {
                try {
                    jdbc.update("UPDATE leaf_alloc SET max_id=max_id+step WHERE biz_tag='short_url'");
                    var row = jdbc.queryForMap(
                        "SELECT max_id, step FROM leaf_alloc WHERE biz_tag='short_url'");
                    long max = ((Number) row.get("max_id")).longValue();
                    int step = ((Number) row.get("step")).intValue();
                    next = new Segment(max - step + 1, max);
                } finally {
                    loading.set(false);
                }
            });
        }
    }
}
```

**性能**：`nextId()` 纯 CAS 操作，单机 > 500 万次/s；DB 交互频率 ≈ 每 100 秒一次。

------

### 4.2 Base62 编码

```java
public class Base62 {
    private static final char[] CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public static String encode(long id) {
        char[] buf = new char[7];
        for (int i = 6; i >= 0; i--) {
            buf[i] = CHARS[(int)(id % 62)];
            id /= 62;
        }
        return new String(buf);
    }

    public static long decode(String code) {
        long r = 0;
        for (char c : code.toCharArray()) {
            r = r * 62 + indexOf(c);
        }
        return r;
    }
}
```

------

### 4.3 存储层（ShardingSphere 分库分表）

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        tables:
          short_url:
            actual-data-nodes: ds${0..3}.short_url_${0..63}   # 4库×64表=256片
            database-strategy:
              standard:
                sharding-column: short_code
                sharding-algorithm-name: db-hash-mod
            table-strategy:
              standard:
                sharding-column: short_code
                sharding-algorithm-name: tb-hash-mod
CREATE TABLE short_url_0000 (
    id          BIGINT PRIMARY KEY,
    short_code  VARCHAR(8) NOT NULL,
    long_url    VARCHAR(2048) NOT NULL,
    expire_time DATETIME,
    status      TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (short_code)
) ENGINE=InnoDB;
```

------

### 4.4 三级缓存读路径

```
请求 → 布隆过滤器(排除不存在的码)
         │ 存在
         ▼
      L1: Caffeine 本地缓存 (5万条, 30s TTL)
         │ miss
         ▼
      L2: Redis Cluster (24h TTL)
         │ miss
         ▼
      L3: MySQL (ShardingSphere)
         │ 回填 L2 + L1
         ▼
      返回 302
public Mono<ShortUrlDO> resolve(String code) {
    // L1
    ShortUrlDO hit = caffeine.getIfPresent(code);
    if (hit != null) return Mono.just(hit);

    // 布隆过滤器
    if (!bloomFilter.contains(code)) return Mono.empty();

    // L2 → L3
    return redisGet(code)
        .switchIfEmpty(mysqlGet(code)
            .doOnNext(e -> { redisSet(code, e); caffeine.put(code, e); }))
        .doOnNext(e -> caffeine.put(code, e));
}
```

------

### 4.5 跳转接口（WebFlux）

```java
@GetMapping("/{code}")
public Mono<ResponseEntity<Void>> redirect(@PathVariable String code,
                                           ServerHttpRequest req) {
    return readService.resolve(code)
        .filter(e -> e.getStatus() == 1 && !e.isExpired())
        .map(e -> {
            // 异步投递统计（纳秒级，不阻塞）
            statsCollector.publish(code, buildVisitorId(req));

            return ResponseEntity.status(302)
                .header("Location", e.getLongUrl())
                .header("Cache-Control", "no-store")
                .<Void>build();
        })
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

------

## 五、轻量统计模块（Disruptor + Redis）

### 5.1 数据流

```
跳转响应 ──► Disruptor RingBuffer(65536)
                    │
                    │ 每256条 或 200ms 触发
                    ▼
             Redis Pipeline 批量写入
             ┌─────────────────────────┐
             │ INCR  s:pv:{code}:{date}│  当日PV
             │ PFADD s:uv:{code}:{date}│  当日UV (HyperLogLog)
             │ INCR  s:pv:t:{code}     │  累计PV
             └─────────────────────────┘
                    │
                    │ 每小时 @Scheduled 归档
                    ▼
             MySQL short_url_stats 表
```

### 5.2 Redis Key 规划

| Key                      | 类型        | TTL      | 说明               |
| ------------------------ | ----------- | -------- | ------------------ |
| `s:pv:{code}:{yyyyMMdd}` | String      | 48h      | 当日 PV            |
| `s:uv:{code}:{yyyyMMdd}` | HyperLogLog | 48h      | 当日 UV（12KB/个） |
| `s:pv:t:{code}`          | String      | 永不过期 | 累计 PV            |

### 5.3 核心代码

```java
@Component
public class StatsCollector {
    private final RingBuffer<ClickEvent> ring;

    public StatsCollector(RedissonClient redis) {
        Disruptor<ClickEvent> d = new Disruptor<>(
            ClickEvent::new, 1 << 16,
            DaemonThreadFactory.INSTANCE,
            ProducerType.MULTI, new SleepingWaitStrategy());
        d.handleEventsWith(new BatchFlushHandler(redis, 256, 200));
        d.start();
        this.ring = d.getRingBuffer();
    }

    public void publish(String code, String visitorId) {
        long seq = ring.next();
        try {
            ClickEvent e = ring.get(seq);
            e.code = code;
            e.visitorId = visitorId;
        } finally {
            ring.publish(seq);
        }
    }
}
// 批量刷写Handler
public class BatchFlushHandler implements EventHandler<ClickEvent> {
    private final List<ClickEvent> buf = new ArrayList<>(512);

    @Override
    public void onEvent(ClickEvent e, long seq, boolean endOfBatch) {
        buf.add(e);
        if (buf.size() >= batchSize || endOfBatch) flush();
    }

    private void flush() {
        RBatch batch = redis.createBatch();
        String date = today();
        for (ClickEvent e : buf) {
            batch.getAtomicLong("s:pv:" + e.code + ":" + date).incrementAndGetAsync();
            batch.getHyperLogLog("s:uv:" + e.code + ":" + date).addAsync(e.visitorId);
            batch.getAtomicLong("s:pv:t:" + e.code).incrementAndGetAsync();
        }
        batch.execute();  // 一次RTT
        buf.clear();
    }
}
```

### 5.4 统计查询 API

```java
@GetMapping("/api/stats/{code}")
public Mono<StatsVO> stats(@PathVariable String code) {
    String date = today();
    return Mono.zip(
        redis.getAtomicLong("s:pv:" + code + ":" + date).getAsync(),
        redis.getHyperLogLog("s:uv:" + code + ":" + date).countAsync(),
        redis.getAtomicLong("s:pv:t:" + code).getAsync()
    ).map(t -> new StatsVO(code, t.getT1(), t.getT2(), t.getT3()));
}
```

### 5.5 归档表

```sql
CREATE TABLE short_url_stats (
    short_code VARCHAR(8) NOT NULL,
    stat_date  DATE NOT NULL,
    pv         BIGINT DEFAULT 0,
    uv         BIGINT DEFAULT 0,
    PRIMARY KEY (short_code, stat_date)
);
```

每小时 `@Scheduled` 将 Redis 中前一天数据 upsert 到此表，供历史趋势查询。

------

## 六、写流程 & 读流程总结

### 生成短链

```
1. 校验 URL 合法性 + 黑名单
2. (可选) 幂等查重
3. 发号器 nextId()          ← 纯内存
4. Base62.encode(id)
5. INSERT MySQL (ShardingSphere路由)
6. 预热 Redis 缓存
7. 布隆过滤器 add(code)
8. 返回 https://s.cn/Ab3xK9p
```

### 短链跳转

```
1. 布隆过滤器判断 → 不存在直接404
2. Caffeine → Redis → MySQL 三级查找
3. 校验 status / expire_time
4. Disruptor.publish(点击事件)   ← 异步，不阻塞
5. 返回 302 + Location
```

------

## 七、高可用与容灾

| 故障场景         | 应对策略                                     |
| ---------------- | -------------------------------------------- |
| Redis 主节点宕机 | Redis Cluster 自动故障转移（从节点提升）     |
| Redis 全挂       | 降级直读 MySQL（限流保护DB）                 |
| 发号器 DB 不可用 | 内存号段仍有 10 万 ID 余量，争取恢复时间     |
| 单个应用节点挂   | 无状态服务，K8s 自动重启 + LB 摘除           |
| MySQL 主库挂     | MHA / Orchestrator 自动切主                  |
| 缓存穿透         | 布隆过滤器 + 空值短 TTL 缓存                 |
| 缓存雪崩         | TTL 加随机抖动；热点 Key 永不过期 + 异步刷新 |

------

## 八、安全设计

- 长 URL 入库前做**恶意域名黑名单**校验
- 短码 ID 起始值 10 亿 + 可选混淆乘数，**防枚举遍历**
- 网关层**令牌桶限流**（每 IP 100 req/s）
- 管理接口 API Key 鉴权
- 支持紧急下线：`UPDATE status=0` + Redis DEL，秒级生效

------

## 九、容量与性能估算

| 项目         | 数值                                                     |
| ------------ | -------------------------------------------------------- |
| 5 年数据量   | 1800 亿行 × 300B ≈ 54 TB → 256 分片 + 冷归档             |
| Redis 缓存   | 热点 1 亿条 × 120B ≈ **12 GB**                           |
| Redis 统计   | 日活 100 万短链 × 3 Key × 100B ≈ **300 MB**              |
| 读 QPS 50 万 | Caffeine 挡 70% + Redis 挡 25% + DB 仅 5%                |
| 单节点(4C8G) | 跳转 3.5 万 QPS（Redis 命中）/ 12 万 QPS（本地缓存命中） |

------

## 十、工程结构

```
├── short-url-gateway/              # Spring Cloud Gateway（限流/路由）
├── short-url-common/               # Base62、DTO、工具类
├── short-url-core/                 # 发号器、缓存抽象、布隆过滤器
├── short-url-write/                # 生成短链服务
├── short-url-read/                 # 跳转服务 (WebFlux)
├── short-url-stats/                # 统计收集器 + 归档任务
├── short-url-admin/                # 管理后台（查询/下线/配置）
└── deploy/
    ├── docker-compose.yml          # 本地开发一键启动
    ├── k8s/                        # 生产 K8s 编排
    └── nginx/
        └── lua/hot_shortcut.lua    # Nginx层热点短路(可选)
```

------

## 十一、Maven 核心依赖

```xml
<dependencies>
    <!-- WebFlux -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <!-- Redisson -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>3.27.0</version>
    </dependency>
    <!-- ShardingSphere -->
    <dependency>
        <groupId>org.apache.shardingsphere</groupId>
        <artifactId>shardingsphere-jdbc</artifactId>
        <version>5.5.0</version>
    </dependency>
    <!-- Disruptor -->
    <dependency>
        <groupId>com.lmax</groupId>
        <artifactId>disruptor</artifactId>
        <version>4.0.0</version>
    </dependency>
    <!-- Caffeine -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>
    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.7</version>
    </dependency>
</dependencies>
```

------

## 十二、一句话总结

> **Nginx → Gateway → WebFlux 应用（Caffeine + Redis + MySQL 三级缓存）→ Disruptor 异步写 Redis 统计 → 定时归档 MySQL**

------

