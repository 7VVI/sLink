package com.shortlink.core.stats;

import com.lmax.disruptor.EventHandler;
import com.shortlink.common.constant.ShortLinkKeys;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量刷写 Handler：攒批后通过一次 Redis Pipeline 写入 PV/UV/累计 PV。
 *
 * <p>触发条件：达到批次阈值、批尾（endOfBatch）或时间窗口到期。
 * 由于事件槽会被 RingBuffer 复用，接受事件时必须拷贝不可变字段引用。</p>
 */
public class StatsFlushHandler implements EventHandler<ClickEvent> {

    private static final Logger log = LoggerFactory.getLogger(StatsFlushHandler.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 归档辅助 Set 的 TTL 需覆盖统计 Key 的 48h 生命周期。
     */
    private static final Duration CODES_OF_DAY_TTL = Duration.ofHours(49);

    /**
     * 当日 PV/UV 键的保留时长（归档完成后自动过期）。
     */
    private static final Duration DAY_STATS_TTL = Duration.ofHours(48);

    private final RedissonClient redisson;

    private final int batchSize;

    private final long flushIntervalMillis;

    private final List<Click> buffer;

    private long lastFlushMillis = System.currentTimeMillis();

    StatsFlushHandler(RedissonClient redisson, int batchSize, long flushIntervalMillis) {
        this.redisson = redisson;
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.buffer = new ArrayList<>(batchSize);
    }

    @Override
    public void onEvent(ClickEvent event, long sequence, boolean endOfBatch) {
        buffer.add(new Click(event.code, event.visitorId));
        long now = System.currentTimeMillis();
        if (buffer.size() >= batchSize || endOfBatch || now - lastFlushMillis >= flushIntervalMillis) {
            flush();
        }
    }

    /**
     * 单条点击事件（事件槽复用安全）。
     */
    private record Click(String code, String visitorId) {
    }

    /**
     * 消费线程停止后的兜底刷写，可由外部线程在 shutdown 之后调用一次。
     */
    void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        try {
            RBatch batch = redisson.createBatch();
            String date = LocalDate.now().format(DATE_FORMATTER);
            Set<String> distinctCodes = new HashSet<>();
            for (Click click : buffer) {
                batch.getAtomicLong(ShortLinkKeys.pvOfDay(click.code(), date)).incrementAndGetAsync();
                batch.getHyperLogLog(ShortLinkKeys.uvOfDay(click.code(), date)).addAsync(click.visitorId());
                batch.getAtomicLong(ShortLinkKeys.pvTotal(click.code())).incrementAndGetAsync();
                distinctCodes.add(click.code());
            }
            // 记录当日有点击的短码集合，供定时归档反查
            batch.getSet(ShortLinkKeys.codesOfDay(date)).addAllAsync(distinctCodes);
            batch.getSet(ShortLinkKeys.codesOfDay(date)).expireAsync(CODES_OF_DAY_TTL);
            // 当日 PV/UV 键设置 48h TTL（累计 PV 键永不过期）
            for (String code : distinctCodes) {
                batch.getBucket(ShortLinkKeys.pvOfDay(code, date)).expireAsync(DAY_STATS_TTL);
                batch.getHyperLogLog(ShortLinkKeys.uvOfDay(code, date)).expireAsync(DAY_STATS_TTL);
            }
            batch.execute();
        } catch (Exception e) {
            // 统计允许最终一致，失败丢弃本批并记录
            log.error("统计批量写入 Redis 失败，丢弃 {} 条点击", buffer.size(), e);
        } finally {
            buffer.clear();
            lastFlushMillis = System.currentTimeMillis();
        }
    }
}
