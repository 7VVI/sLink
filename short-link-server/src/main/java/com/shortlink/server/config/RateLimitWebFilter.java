package com.shortlink.server.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.server.support.WebSupport;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * API 令牌桶限流（按 IP，Redisson RRateLimiter 实现，等价网关层限流）。
 *
 * <p>仅作用于 /api/**；限流键的初始化标记做本地缓存，避免每次请求多一次 RTT。</p>
 */
@Component
@Order(RateLimitWebFilter.ORDER)
public class RateLimitWebFilter implements WebFilter {

    public static final int ORDER = -100;

    private final RedissonClient redisson;

    private final ShortLinkProperties.RateLimit config;

    /**
     * 已初始化限流键的本地标记（过期自动清理，防止内存膨胀）。
     */
    private final Cache<String, Boolean> initializedKeys = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public RateLimitWebFilter(RedissonClient redisson, ShortLinkProperties properties) {
        this.redisson = redisson;
        this.config = properties.getRateLimit();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!config.isEnabled() || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String key = ShortLinkKeys.rateLimit(WebSupport.clientIp(exchange.getRequest()));
        return Mono.fromCallable(() -> tryAcquire(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(acquired -> acquired
                        ? chain.filter(exchange)
                        : Mono.error(new BizException(ErrorCode.TOO_MANY_REQUESTS)));
    }

    private boolean tryAcquire(String key) {
        RRateLimiter limiter = redisson.getRateLimiter(key);
        if (initializedKeys.getIfPresent(key) == null) {
            // trySetRate 仅在限流器不存在时生效，幂等
            limiter.trySetRate(RateType.OVERALL, config.getPerIpQps(), 1, RateIntervalUnit.SECONDS);
            initializedKeys.put(key, Boolean.TRUE);
        }
        return limiter.tryAcquire();
    }
}
