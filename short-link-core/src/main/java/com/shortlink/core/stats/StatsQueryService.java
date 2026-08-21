package com.shortlink.core.stats;

import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.common.dto.StatsVO;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 实时统计查询（当日 PV / 当日 UV / 累计 PV），全部走 Redis 非阻塞接口。
 */
@Component
public class StatsQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonReactiveClient redisson;

    public StatsQueryService(RedissonReactiveClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 查询指定短码的实时统计。
     */
    public Mono<StatsVO> realtime(String code) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        Mono<Long> todayPv = redisson.getAtomicLong(ShortLinkKeys.pvOfDay(code, date)).get().defaultIfEmpty(0L);
        Mono<Long> todayUv = redisson.getHyperLogLog(ShortLinkKeys.uvOfDay(code, date)).count().defaultIfEmpty(0L);
        Mono<Long> totalPv = redisson.getAtomicLong(ShortLinkKeys.pvTotal(code)).get().defaultIfEmpty(0L);
        return Mono.zip(todayPv, todayUv, totalPv)
                .map(tuple -> new StatsVO(code, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }
}
