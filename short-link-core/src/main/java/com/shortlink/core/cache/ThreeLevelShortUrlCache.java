package com.shortlink.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.core.config.ShortLinkProperties;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 三级缓存中的 L1（Caffeine 本地缓存）与 L2（Redis）。
 *
 * <p>L3（MySQL）回填由领域服务编排。读路径：L1 → 布隆过滤器 → L2 → L3。
 * 写入 L2 的 TTL 附加 ±10% 随机抖动，避免集中过期造成雪崩。</p>
 */
@Component
public class ThreeLevelShortUrlCache {

    /**
     * L1 值为 Optional：Optional.empty 表示“确认不存在的空值缓存”。
     */
    private final Cache<String, Optional<ShortUrlCacheValue>> l1;

    private final RedissonClient redisson;

    private final RedissonReactiveClient redissonReactive;

    private final ShortLinkProperties.Cache config;

    public ThreeLevelShortUrlCache(RedissonClient redisson,
                                   RedissonReactiveClient redissonReactive,
                                   ShortLinkProperties properties) {
        this.redisson = redisson;
        this.redissonReactive = redissonReactive;
        this.config = properties.getCache();
        this.l1 = Caffeine.newBuilder()
                .maximumSize(config.getL1MaximumSize())
                .expireAfterWrite(config.getL1ExpireAfterWrite())
                .recordStats()
                .build();
    }

    /**
     * L1 读取：外层 Optional 表示“是否命中 L1”，内层 Optional.empty 表示空值缓存。
     */
    public Optional<Optional<ShortUrlCacheValue>> getFromL1(String code) {
        return Optional.ofNullable(l1.getIfPresent(code));
    }

    /**
     * L2 读取（非阻塞），key 不存在时 Mono 为空；空值哨兵会作为正常值返回。
     */
    public Mono<ShortUrlCacheValue> getFromL2(String code) {
        RBucketReactive<ShortUrlCacheValue> bucket =
                redissonReactive.getBucket(ShortLinkKeys.shortUrlCache(code));
        return bucket.get();
    }

    /**
     * 回填缓存（L1 + L2）。内部使用同步 Redis 写入，需在 boundedElastic 等允许阻塞的线程调用。
     */
    public void put(String code, ShortUrlCacheValue value) {
        l1.put(code, Optional.of(value));
        bucket(code).set(value, withJitter(config.getL2Expire()));
    }

    /**
     * 写入空值缓存（防穿透），同样要求允许阻塞。
     */
    public void putNegative(String code) {
        l1.put(code, Optional.empty());
        bucket(code).set(ShortUrlCacheValue.EMPTY, withJitter(config.getNegativeExpire()));
    }

    /**
     * 失效缓存（下线/恢复/删除时调用）。
     */
    public void evict(String code) {
        l1.invalidate(code);
        bucket(code).delete();
    }

    /**
     * L1 本地缓存实例（供监控统计使用）。
     */
    public Cache<String, Optional<ShortUrlCacheValue>> l1Cache() {
        return l1;
    }

    private RBucket<ShortUrlCacheValue> bucket(String code) {
        return redisson.getBucket(ShortLinkKeys.shortUrlCache(code));
    }

    /**
     * TTL 附加 ±10% 随机抖动：base * [0.9, 1.1)。
     */
    private Duration withJitter(Duration base) {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble(0.2);
        return Duration.ofMillis((long) (base.toMillis() * factor));
    }
}
