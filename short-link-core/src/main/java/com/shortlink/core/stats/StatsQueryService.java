package com.shortlink.core.stats;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.common.dto.StatsVO;
import com.shortlink.core.dal.entity.ShortUrlStatsDO;
import com.shortlink.core.dal.mapper.ShortUrlStatsMapper;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时统计查询（当日 PV / 当日 UV / 累计 PV / 累计 UV）。
 *
 * <p>当日与累计 PV 走 Redis；累计 UV = 归档表（stat_date &lt; 今日）SUM(uv) + 当日 HLL，
 * 因归档任务按天覆盖写，今日归档行不完整，直接累加会重复计数。</p>
 */
@Component
public class StatsQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonReactiveClient redisson;

    private final RedissonClient redissonSync;

    private final ShortUrlStatsMapper statsMapper;

    public StatsQueryService(RedissonReactiveClient redisson,
                             RedissonClient redissonSync,
                             ShortUrlStatsMapper statsMapper) {
        this.redisson = redisson;
        this.redissonSync = redissonSync;
        this.statsMapper = statsMapper;
    }

    /**
     * 查询指定短码的实时统计。
     */
    public Mono<StatsVO> realtime(String code) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        Mono<Long> todayPv = redisson.getAtomicLong(ShortLinkKeys.pvOfDay(code, date)).get().defaultIfEmpty(0L);
        Mono<Long> todayUv = redisson.getHyperLogLog(ShortLinkKeys.uvOfDay(code, date)).count().defaultIfEmpty(0L);
        Mono<Long> totalPv = redisson.getAtomicLong(ShortLinkKeys.pvTotal(code)).get().defaultIfEmpty(0L);
        Mono<Long> archivedUv = Mono.fromCallable(() -> archivedUvBeforeToday(List.of(code))
                        .getOrDefault(code, 0L))
                .subscribeOn(Schedulers.boundedElastic());
        return Mono.zip(todayPv, todayUv, totalPv, archivedUv)
                .map(tuple -> new StatsVO(code, tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4() + tuple.getT2()));
    }

    /**
     * 批量查询多个短码的实时统计（列表页用）：
     * Redis 走一次 RBatch 网络往返，累计 UV 用一条 GROUP BY 汇总。须在可阻塞线程调用。
     */
    public Map<String, StatsVO> realtimeBatch(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        String date = LocalDate.now().format(DATE_FORMATTER);
        List<String> order = List.copyOf(codes);
        RBatch batch = redissonSync.createBatch();
        List<RFuture<Long>> pvFutures = new ArrayList<>(order.size());
        List<RFuture<Long>> uvFutures = new ArrayList<>(order.size());
        List<RFuture<Long>> totalFutures = new ArrayList<>(order.size());
        for (String code : order) {
            pvFutures.add(batch.getAtomicLong(ShortLinkKeys.pvOfDay(code, date)).getAsync());
            uvFutures.add(batch.getHyperLogLog(ShortLinkKeys.uvOfDay(code, date)).countAsync());
            totalFutures.add(batch.getAtomicLong(ShortLinkKeys.pvTotal(code)).getAsync());
        }
        batch.execute();

        Map<String, Long> archivedUv = archivedUvBeforeToday(order);
        Map<String, StatsVO> result = new HashMap<>(order.size() * 2);
        for (int i = 0; i < order.size(); i++) {
            String code = order.get(i);
            long todayUv = uvFutures.get(i).join();
            result.put(code, new StatsVO(code,
                    pvFutures.get(i).join(),
                    todayUv,
                    totalFutures.get(i).join(),
                    archivedUv.getOrDefault(code, 0L) + todayUv));
        }
        return result;
    }

    /**
     * 归档表中 stat_date &lt; 今日 的 UV 汇总（今日行不完整，不计入）。须在可阻塞线程调用。
     */
    private Map<String, Long> archivedUvBeforeToday(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ShortUrlStatsDO> wrapper = new QueryWrapper<ShortUrlStatsDO>()
                .select("short_code", "SUM(uv) AS uv")
                .lt("stat_date", LocalDate.now())
                .in("short_code", codes)
                .groupBy("short_code");
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : statsMapper.selectMaps(wrapper)) {
            Object code = row.get("short_code");
            Object uv = row.get("uv");
            if (code != null && uv instanceof Number number) {
                result.put(code.toString(), number.longValue());
            }
        }
        return result;
    }
}
