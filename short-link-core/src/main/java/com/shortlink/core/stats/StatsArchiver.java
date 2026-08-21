package com.shortlink.core.stats;

import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.core.dal.entity.ShortUrlStatsDO;
import com.shortlink.core.dal.mapper.ShortUrlStatsMapper;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 统计归档任务：每小时将 Redis 中“今天 + 昨天”的 PV/UV upsert 到 MySQL，
 * 供历史趋势查询。重复归档幂等（按 (short_code, stat_date) 覆盖写）。
 */
@Component
public class StatsArchiver {

    private static final Logger log = LoggerFactory.getLogger(StatsArchiver.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonClient redisson;

    private final ShortUrlStatsMapper statsMapper;

    private final ShortLinkProperties properties;

    public StatsArchiver(RedissonClient redisson,
                         ShortUrlStatsMapper statsMapper,
                         ShortLinkProperties properties) {
        this.redisson = redisson;
        this.statsMapper = statsMapper;
        this.properties = properties;
    }

    /**
     * 每小时第 10 分钟执行（避开整点业务高峰）。运行在调度线程，可安全阻塞。
     */
    @Scheduled(cron = "0 10 * * * ?")
    public void archive() {
        archiveDay(LocalDate.now());
        archiveDay(LocalDate.now().minusDays(1));
    }

    void archiveDay(LocalDate day) {
        String date = day.format(DATE_FORMATTER);
        RSet<String> codes = redisson.getSet(ShortLinkKeys.codesOfDay(date));
        if (codes.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        int count = 0;
        for (String code : codes.readAll()) {
            long pv = redisson.getAtomicLong(ShortLinkKeys.pvOfDay(code, date)).get();
            long uv = redisson.getHyperLogLog(ShortLinkKeys.uvOfDay(code, date)).count();
            ShortUrlStatsDO row = new ShortUrlStatsDO();
            row.setShortCode(code);
            row.setStatDate(day);
            row.setPv(pv);
            row.setUv(uv);
            statsMapper.upsert(row);
            count++;
        }
        log.info("统计归档完成: date={}, codes={}, cost={}ms", date, count, System.currentTimeMillis() - start);
    }
}
