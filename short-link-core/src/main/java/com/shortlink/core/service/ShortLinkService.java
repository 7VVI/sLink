package com.shortlink.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.CreateShortLinkReq;
import com.shortlink.common.dto.ShortLinkDetailVO;
import com.shortlink.common.dto.ShortLinkVO;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.common.util.Base62;
import com.shortlink.common.result.PageResult;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.bloom.ShortUrlBloomFilter;
import com.shortlink.core.cache.ShortUrlCacheValue;
import com.shortlink.core.cache.ThreeLevelShortUrlCache;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.core.dal.entity.ShortUrlDO;
import com.shortlink.core.dal.mapper.ShortUrlMapper;
import com.shortlink.core.id.SegmentIdGenerator;
import com.shortlink.core.stats.StatsQueryService;
import com.shortlink.core.support.Reactors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 短链领域服务：创建、跳转解析（三级缓存）、管理与查询。
 */
@Service
public class ShortLinkService {

    private final ShortUrlMapper shortUrlMapper;

    private final SegmentIdGenerator idGenerator;

    private final ThreeLevelShortUrlCache cache;

    private final ShortUrlBloomFilter bloomFilter;

    private final StatsQueryService statsQueryService;

    private final ShortLinkProperties properties;

    public ShortLinkService(ShortUrlMapper shortUrlMapper,
                            SegmentIdGenerator idGenerator,
                            ThreeLevelShortUrlCache cache,
                            ShortUrlBloomFilter bloomFilter,
                            StatsQueryService statsQueryService,
                            ShortLinkProperties properties) {
        this.shortUrlMapper = shortUrlMapper;
        this.idGenerator = idGenerator;
        this.cache = cache;
        this.bloomFilter = bloomFilter;
        this.statsQueryService = statsQueryService;
        this.properties = properties;
    }

    /**
     * 创建短链：校验 → 发号 → Base62 → 入库 → 布隆过滤器 + 缓存预热。
     */
    public Mono<ShortLinkVO> create(CreateShortLinkReq request, long userId) {
        return Reactors.call(() -> {
            UrlValidator.requireValid(request.longUrl(), properties.getSecurity().getBlacklistDomains());
            long id = idGenerator.nextId();
            String code = Base62.encode(id);

            ShortUrlDO record = new ShortUrlDO();
            record.setId(id);
            record.setShortCode(code);
            record.setLongUrl(request.longUrl().trim());
            record.setTitle(request.title());
            record.setUserId(userId);
            record.setStatus(ShortLinkConstants.STATUS_ENABLED);
            record.setExpireTime(resolveExpireTime(request.expireDays()));
            LocalDateTime now = LocalDateTime.now();
            record.setCreateTime(now);
            record.setUpdateTime(now);
            shortUrlMapper.insert(record);

            bloomFilter.add(code);
            cache.put(code, ShortUrlCacheValue.of(record.getLongUrl(), record.getStatus(),
                    toExpireMillis(record.getExpireTime())));
            return toVO(record);
        });
    }

    /**
     * 跳转解析：L1 → 布隆过滤器 → L2 → L3（回填），返回当前有效的短链，否则为空。
     */
    public Mono<Optional<ShortUrlCacheValue>> resolve(String code) {
        if (!ShortLinkConstants.SHORT_CODE_PATTERN.matcher(code).matches()) {
            return Mono.just(Optional.empty());
        }

        // L1：本地缓存（含空值缓存）
        Optional<Optional<ShortUrlCacheValue>> l1Hit = cache.getFromL1(code);
        if (l1Hit.isPresent()) {
            return Mono.just(l1Hit.get().filter(ShortUrlCacheValue::isEffectiveNow));
        }

        // 布隆过滤器拦截不存在的短码（未就绪/重建期间 fail-open）
        if (!bloomFilter.mightContain(code)) {
            cache.putNegative(code);
            return Mono.just(Optional.empty());
        }

        // L2 → L3
        return cache.getFromL2(code)
                .map(Optional::of)
                .switchIfEmpty(Mono.defer(() -> loadFromDatabase(code)))
                .map(opt -> opt.filter(ShortUrlCacheValue::isEffectiveNow));
    }

    /**
     * L3 回源：查 MySQL 并回填 L2/L1；未命中写入空值缓存防穿透。
     */
    private Mono<Optional<ShortUrlCacheValue>> loadFromDatabase(String code) {
        return Reactors.call(() -> shortUrlMapper.selectOne(new LambdaQueryWrapper<ShortUrlDO>()
                        .eq(ShortUrlDO::getShortCode, code)))
                .map(this::toCacheValue)
                .doOnNext(value -> cache.put(code, value))
                .map(Optional::of)
                .switchIfEmpty(Mono.defer(() -> Reactors.call(() -> {
                    cache.putNegative(code);
                    return Optional.<ShortUrlCacheValue>empty();
                })));
    }

    /**
     * 当前用户的短链分页列表。
     */
    public Mono<PageResult<ShortLinkVO>> pageByUser(long userId, long pageNo, long pageSize) {
        return Reactors.call(() -> {
            Page<ShortUrlDO> page = shortUrlMapper.selectPage(new Page<>(pageNo, pageSize),
                    new LambdaQueryWrapper<ShortUrlDO>()
                            .eq(ShortUrlDO::getUserId, userId)
                            .ne(ShortUrlDO::getStatus, ShortLinkConstants.STATUS_DELETED)
                            .orderByDesc(ShortUrlDO::getId));
            return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                    page.getRecords().stream().map(this::toVO).toList());
        });
    }

    /**
     * 全部用户的短链分页列表（管理端）。
     */
    public Mono<PageResult<ShortLinkVO>> pageAll(long pageNo, long pageSize, Integer status) {
        return Reactors.call(() -> {
            LambdaQueryWrapper<ShortUrlDO> wrapper = new LambdaQueryWrapper<ShortUrlDO>()
                    .orderByDesc(ShortUrlDO::getId);
            if (status != null) {
                wrapper.eq(ShortUrlDO::getStatus, status);
            }
            Page<ShortUrlDO> page = shortUrlMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                    page.getRecords().stream().map(this::toVO).toList());
        });
    }

    /**
     * 短链详情（含实时统计），非本人且非管理员抛出无权访问。
     */
    public Mono<ShortLinkDetailVO> detail(String code, long userId, boolean admin) {
        return Reactors.call(() -> requireReadable(code, userId, admin))
                .flatMap(record -> statsQueryService.realtime(code)
                        .map(stats -> toDetailVO(record, stats)));
    }

    /**
     * 校验当前用户对短码的读权限。
     */
    public Mono<Void> assertReadable(String code, long userId, boolean admin) {
        return Reactors.call(() -> {
            requireReadable(code, userId, admin);
            return null;
        }).then();
    }

    /**
     * 上线/下线短链：更新 DB 后失效缓存，秒级生效。
     */
    public Mono<Void> changeStatus(String code, boolean enabled, long userId, boolean admin) {
        int targetStatus = enabled ? ShortLinkConstants.STATUS_ENABLED : ShortLinkConstants.STATUS_DISABLED;
        return updateStatusInternal(code, targetStatus, userId, admin);
    }

    /**
     * 删除短链（逻辑删除）。
     */
    public Mono<Void> remove(String code, long userId, boolean admin) {
        return updateStatusInternal(code, ShortLinkConstants.STATUS_DELETED, userId, admin);
    }

    private Mono<Void> updateStatusInternal(String code, int targetStatus, long userId, boolean admin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            ShortUrlDO update = new ShortUrlDO();
            update.setId(record.getId());
            update.setStatus(targetStatus);
            update.setUpdateTime(LocalDateTime.now());
            shortUrlMapper.updateById(update);
            cache.evict(code);
            return null;
        }).then();
    }

    private ShortUrlDO requireReadable(String code, long userId, boolean admin) {
        ShortUrlDO record = shortUrlMapper.selectOne(new LambdaQueryWrapper<ShortUrlDO>()
                .eq(ShortUrlDO::getShortCode, code));
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "短链不存在: " + code);
        }
        if (!admin && record.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    private LocalDateTime resolveExpireTime(Integer expireDays) {
        if (expireDays == null) {
            return null;
        }
        return LocalDateTime.now().plusDays(expireDays);
    }

    private long toExpireMillis(LocalDateTime expireTime) {
        if (expireTime == null) {
            return 0L;
        }
        return expireTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private ShortUrlCacheValue toCacheValue(ShortUrlDO record) {
        return ShortUrlCacheValue.of(record.getLongUrl(), record.getStatus(),
                toExpireMillis(record.getExpireTime()));
    }

    private ShortLinkVO toVO(ShortUrlDO record) {
        return new ShortLinkVO(record.getShortCode(), properties.getDomain() + "/" + record.getShortCode(),
                record.getLongUrl(), record.getTitle(), record.getStatus(),
                record.getExpireTime(), record.getCreateTime());
    }

    private ShortLinkDetailVO toDetailVO(ShortUrlDO record,
                                         com.shortlink.common.dto.StatsVO stats) {
        return new ShortLinkDetailVO(record.getShortCode(),
                properties.getDomain() + "/" + record.getShortCode(),
                record.getLongUrl(), record.getTitle(), record.getStatus(),
                record.getExpireTime(), record.getCreateTime(), stats);
    }
}
