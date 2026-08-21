package com.shortlink.core.bloom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.core.dal.entity.ShortUrlDO;
import com.shortlink.core.dal.mapper.ShortUrlMapper;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Redisson 的短码存在性布隆过滤器（防缓存穿透）。
 *
 * <p>首次启动或配置重建时，按 id 游标分页全量加载 short_code。
 * 重建期间 fail-open（一律放行走 L2/L3），重建期间新建的短码进入
 * 补偿队列，重建结束后统一补写，避免重建竞态导致永久误判。</p>
 */
@Component
public class ShortUrlBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlBloomFilter.class);

    private static final int REBUILD_PAGE_SIZE = 5_000;

    private final RedissonClient redisson;

    private final ShortUrlMapper shortUrlMapper;

    private final ShortLinkProperties.Bloom config;

    private final AtomicBoolean ready = new AtomicBoolean(false);

    private volatile boolean rebuilding;

    /**
     * 重建期间新建短码的补偿队列。
     */
    private final ConcurrentLinkedQueue<String> pendingAdds = new ConcurrentLinkedQueue<>();

    private volatile RBloomFilter<String> filter;

    public ShortUrlBloomFilter(RedissonClient redisson,
                               ShortUrlMapper shortUrlMapper,
                               ShortLinkProperties properties) {
        this.redisson = redisson;
        this.shortUrlMapper = shortUrlMapper;
        this.config = properties.getBloom();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Mono.fromRunnable(this::init)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 是否“可能存在”。未就绪或重建期间 fail-open 返回 true，保证正确性优先。
     */
    public boolean mightContain(String code) {
        if (!ready.get() || rebuilding) {
            return true;
        }
        return filter.contains(code);
    }

    /**
     * 新建短码时写入过滤器；重建期间进入补偿队列。
     */
    public void add(String code) {
        if (!ready.get()) {
            return;
        }
        if (rebuilding) {
            pendingAdds.add(code);
            return;
        }
        filter.add(code);
    }

    private void init() {
        RBloomFilter<String> bloomFilter = redisson.getBloomFilter(ShortLinkKeys.bloomFilter());
        boolean created = bloomFilter.tryInit(config.getExpectedInsertions(), config.getFalsePositiveRate());
        this.filter = bloomFilter;
        if (created || config.isRebuildOnStartup()) {
            rebuild();
        } else {
            ready.set(true);
            log.info("布隆过滤器已就绪（复用已有过滤器）: expected={}, fpp={}",
                    config.getExpectedInsertions(), config.getFalsePositiveRate());
        }
    }

    /**
     * 全量重建：按 id 升序游标分页扫描全部分片表。
     * 重建失败时保持未就绪（fail-open），避免半成品过滤器造成误判。
     */
    private void rebuild() {
        rebuilding = true;
        boolean success = false;
        try {
            long start = System.currentTimeMillis();
            long sinceId = 0L;
            long count = 0;
            while (true) {
                List<ShortUrlDO> batch = shortUrlMapper.selectList(new LambdaQueryWrapper<ShortUrlDO>()
                        .gt(ShortUrlDO::getId, sinceId)
                        .orderByAsc(ShortUrlDO::getId)
                        .last("LIMIT " + REBUILD_PAGE_SIZE));
                for (ShortUrlDO row : batch) {
                    filter.add(row.getShortCode());
                }
                count += batch.size();
                if (batch.size() < REBUILD_PAGE_SIZE) {
                    break;
                }
                sinceId = batch.get(batch.size() - 1).getId();
            }
            success = true;
            log.info("布隆过滤器重建完成: count={}, cost={}ms", count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("布隆过滤器重建失败，维持 fail-open 模式", e);
        } finally {
            drainPendingAdds();
            rebuilding = false;
            ready.set(success);
        }
    }

    private void drainPendingAdds() {
        String code;
        while ((code = pendingAdds.poll()) != null) {
            filter.add(code);
        }
    }

    @PreDestroy
    public void destroy() {
        drainPendingAdds();
    }
}
