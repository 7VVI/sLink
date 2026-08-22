package com.shortlink.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.constant.ShortLinkKeys;
import com.shortlink.common.dto.CreateShortLinkReq;
import com.shortlink.common.dto.RecycleLinkVO;
import com.shortlink.common.dto.ShortLinkDetailVO;
import com.shortlink.common.dto.ShortLinkVO;
import com.shortlink.common.dto.UpdateShortLinkReq;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.common.result.PageResult;
import com.shortlink.common.util.Base62;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.bloom.ShortUrlBloomFilter;
import com.shortlink.core.cache.ShortUrlCacheValue;
import com.shortlink.core.cache.ThreeLevelShortUrlCache;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.core.dal.entity.LinkGroupDO;
import com.shortlink.core.dal.entity.ShortUrlDO;
import com.shortlink.core.dal.entity.ShortUrlStatsDO;
import com.shortlink.core.dal.entity.SurlDomainDO;
import com.shortlink.core.dal.mapper.LinkGroupMapper;
import com.shortlink.core.dal.mapper.ShortUrlMapper;
import com.shortlink.core.dal.mapper.ShortUrlStatsMapper;
import com.shortlink.core.dal.mapper.SurlDomainMapper;
import com.shortlink.core.id.SegmentIdGenerator;
import com.shortlink.core.stats.StatsQueryService;
import com.shortlink.core.support.Reactors;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 短链领域服务：创建、跳转解析（三级缓存）、分组管理、回收站管理与查询。
 */
@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ShortUrlMapper shortUrlMapper;

    private final ShortUrlStatsMapper statsMapper;

    private final LinkGroupMapper linkGroupMapper;

    private final SurlDomainMapper domainMapper;

    private final DomainService domainService;

    private final SegmentIdGenerator idGenerator;

    private final ThreeLevelShortUrlCache cache;

    private final ShortUrlBloomFilter bloomFilter;

    private final StatsQueryService statsQueryService;

    private final RedissonClient redisson;

    private final ShortLinkProperties properties;

    public ShortLinkService(ShortUrlMapper shortUrlMapper,
                            ShortUrlStatsMapper statsMapper,
                            LinkGroupMapper linkGroupMapper,
                            SurlDomainMapper domainMapper,
                            DomainService domainService,
                            SegmentIdGenerator idGenerator,
                            ThreeLevelShortUrlCache cache,
                            ShortUrlBloomFilter bloomFilter,
                            StatsQueryService statsQueryService,
                            RedissonClient redisson,
                            ShortLinkProperties properties) {
        this.shortUrlMapper = shortUrlMapper;
        this.statsMapper = statsMapper;
        this.linkGroupMapper = linkGroupMapper;
        this.domainMapper = domainMapper;
        this.domainService = domainService;
        this.idGenerator = idGenerator;
        this.cache = cache;
        this.bloomFilter = bloomFilter;
        this.statsQueryService = statsQueryService;
        this.redisson = redisson;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 创建与解析

    /**
     * 创建短链：校验 → 发号 → Base62 → 入库（分组/域名解析）→ 布隆过滤器 + 缓存预热。
     */
    public Mono<ShortLinkVO> create(CreateShortLinkReq request, long userId) {
        return Reactors.call(() -> {
            UrlValidator.requireValid(request.longUrl(), properties.getSecurity().getBlacklistDomains());
            long groupId = resolveGroupId(request.groupId(), userId);
            SurlDomainDO domain = domainService.resolveForCreate(request.domainId());
            String domainPrefix = domain.getDomain();
            long domainId = domain.getId();

            long id = idGenerator.nextId();
            String code = Base62.encode(id);

            ShortUrlDO record = new ShortUrlDO();
            record.setId(id);
            record.setShortCode(code);
            record.setLongUrl(request.longUrl().trim());
            record.setTitle(request.title());
            record.setUserId(userId);
            record.setGroupId(groupId);
            record.setDomainId(domainId);
            record.setStatus(ShortLinkConstants.STATUS_ENABLED);
            record.setExpireTime(resolveExpireTime(request.expireDays()));
            LocalDateTime now = LocalDateTime.now();
            record.setCreateTime(now);
            record.setUpdateTime(now);
            shortUrlMapper.insert(record);

            bloomFilter.add(code);
            cache.put(code, ShortUrlCacheValue.of(record.getLongUrl(), record.getStatus(),
                    toExpireMillis(record.getExpireTime())));
            return toVO(record, groupNameOf(groupId), domainPrefix);
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

    // ------------------------------------------------------------------ 查询

    /**
     * 当前用户的短链分页列表，可按分组过滤（groupId=0 表示未分组）。
     */
    public Mono<PageResult<ShortLinkVO>> pageByUser(long userId, long pageNo, long pageSize, Long groupId) {
        return Reactors.call(() -> {
            LambdaQueryWrapper<ShortUrlDO> wrapper = new LambdaQueryWrapper<ShortUrlDO>()
                    .eq(ShortUrlDO::getUserId, userId)
                    .ne(ShortUrlDO::getStatus, ShortLinkConstants.STATUS_DELETED)
                    .orderByDesc(ShortUrlDO::getId);
            if (groupId != null) {
                wrapper.eq(ShortUrlDO::getGroupId, groupId);
            }
            Page<ShortUrlDO> page = shortUrlMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return toPageResult(page);
        });
    }

    /**
     * 全部用户的短链分页列表（管理端），可按状态过滤。
     */
    public Mono<PageResult<ShortLinkVO>> pageAll(long pageNo, long pageSize, Integer status) {
        return Reactors.call(() -> {
            LambdaQueryWrapper<ShortUrlDO> wrapper = new LambdaQueryWrapper<ShortUrlDO>()
                    .orderByDesc(ShortUrlDO::getId);
            if (status != null) {
                wrapper.eq(ShortUrlDO::getStatus, status);
            }
            Page<ShortUrlDO> page = shortUrlMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return toPageResult(page);
        });
    }

    /**
     * 短链详情（含实时统计），非本人且非管理员抛出无权访问。
     */
    public Mono<ShortLinkDetailVO> detail(String code, long userId, boolean admin) {
        return Reactors.call(() -> {
                    ShortUrlDO record = requireReadable(code, userId, admin);
                    Map<Long, String> groupNames = loadGroupNames(List.of(record));
                    Map<Long, String> domainPrefixes = loadDomainPrefixes(List.of(record));
                    return new DetailArgs(record, groupNames.get(record.getGroupId()),
                            domainPrefix(record, domainPrefixes, loadDefaultDomainPrefix()));
                })
                .flatMap(args -> statsQueryService.realtime(code)
                        .map(stats -> toDetailVO(args, stats)));
    }

    // ------------------------------------------------------------------ 编辑与分组移动

    /**
     * 编辑短链（目标链接/标题/分组）：更新 DB 后失效跳转缓存，秒级生效；统计不受影响。
     */
    public Mono<Void> update(String code, UpdateShortLinkReq request, long userId, boolean admin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            if (record.getStatus() == ShortLinkConstants.STATUS_DELETED) {
                throw new BizException(ErrorCode.PARAM_INVALID, "回收站中的短链不支持编辑，请先还原");
            }
            UrlValidator.requireValid(request.longUrl(), properties.getSecurity().getBlacklistDomains());
            Long groupId = request.groupId() == null ? record.getGroupId() : request.groupId();
            ShortUrlDO update = new ShortUrlDO();
            update.setId(record.getId());
            update.setLongUrl(request.longUrl().trim());
            update.setTitle(request.title());
            update.setGroupId(resolveGroupId(groupId, userId));
            update.setUpdateTime(LocalDateTime.now());
            shortUrlMapper.updateById(update);
            cache.evict(code);
            return null;
        }).then();
    }

    /**
     * 移动短链到指定分组（groupId=0 表示未分组）。分组信息不参与跳转缓存，无需失效缓存。
     */
    public Mono<Void> moveGroup(String code, long groupId, long userId, boolean admin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            requireGroupOwned(groupId, userId);
            ShortUrlDO update = new ShortUrlDO();
            update.setId(record.getId());
            update.setGroupId(groupId);
            update.setUpdateTime(LocalDateTime.now());
            shortUrlMapper.updateById(update);
            return null;
        }).then();
    }

    // ------------------------------------------------------------------ 上下线与回收站

    /**
     * 上线/下线短链：更新 DB 后失效缓存，秒级生效。
     */
    public Mono<Void> changeStatus(String code, boolean enabled, long userId, boolean admin) {
        int targetStatus = enabled ? ShortLinkConstants.STATUS_ENABLED : ShortLinkConstants.STATUS_DISABLED;
        return updateStatusInternal(code, targetStatus, userId, admin, false);
    }

    /**
     * 移入回收站（逻辑删除，保留 retentionDays 天）。
     */
    public Mono<Void> remove(String code, long userId, boolean admin) {
        return updateStatusInternal(code, ShortLinkConstants.STATUS_DELETED, userId, admin, true);
    }

    /**
     * 回收站分页列表（按移入时间倒序）。
     */
    public Mono<PageResult<RecycleLinkVO>> pageRecycled(long userId, long pageNo, long pageSize) {
        return Reactors.call(() -> {
            Page<ShortUrlDO> page = shortUrlMapper.selectPage(new Page<>(pageNo, pageSize),
                    new LambdaQueryWrapper<ShortUrlDO>()
                            .eq(ShortUrlDO::getUserId, userId)
                            .eq(ShortUrlDO::getStatus, ShortLinkConstants.STATUS_DELETED)
                            .orderByDesc(ShortUrlDO::getDeleteTime));
            Map<Long, String> groupNames = loadGroupNames(page.getRecords());
            Map<Long, String> domainPrefixes = loadDomainPrefixes(page.getRecords());
            String defaultPrefix = loadDefaultDomainPrefix();
            List<RecycleLinkVO> records = page.getRecords().stream()
                    .map(record -> {
                        String prefix = domainPrefix(record, domainPrefixes, defaultPrefix);
                        LocalDateTime deleteTime = record.getDeleteTime() == null
                                ? record.getUpdateTime() : record.getDeleteTime();
                        return new RecycleLinkVO(record.getShortCode(), prefix + "/" + record.getShortCode(),
                                record.getLongUrl(), record.getTitle(),
                                groupNames.get(record.getGroupId()), deleteTime,
                                deleteTime.plusDays(properties.getRecycleBin().getRetentionDays()));
                    })
                    .toList();
            return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
        });
    }

    /**
     * 从回收站还原：校验确在回收站后一次性恢复为正常状态并清除删除时间。
     */
    public Mono<Void> restore(String code, long userId, boolean admin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            if (record.getStatus() != ShortLinkConstants.STATUS_DELETED) {
                throw new BizException(ErrorCode.LINK_NOT_IN_RECYCLE);
            }
            ShortUrlDO update = new ShortUrlDO();
            update.setId(record.getId());
            update.setStatus(ShortLinkConstants.STATUS_ENABLED);
            update.setDeleteTime(null);
            update.setUpdateTime(LocalDateTime.now());
            shortUrlMapper.updateById(update);
            cache.evict(code);
            return null;
        }).then();
    }

    /**
     * 彻底删除单条回收站短链（属主或管理员）。
     */
    public Mono<Void> permanentDelete(String code, long userId, boolean admin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            if (record.getStatus() != ShortLinkConstants.STATUS_DELETED) {
                throw new BizException(ErrorCode.LINK_NOT_IN_RECYCLE);
            }
            purgeRecords(List.of(record));
            return null;
        }).then();
    }

    /**
     * 清理回收站中超过保留天数的短链（物理删除 + 缓存/统计清理），返回清理条数。
     * 由定时任务与管理端手动触发调用，须在允许阻塞的线程执行。
     */
    public long purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getRecycleBin().getRetentionDays());
        int batchSize = properties.getRecycleBin().getPurgeBatchSize();
        long purged = 0;
        long sinceId = 0L;
        while (true) {
            List<ShortUrlDO> batch = shortUrlMapper.selectList(new LambdaQueryWrapper<ShortUrlDO>()
                    .eq(ShortUrlDO::getStatus, ShortLinkConstants.STATUS_DELETED)
                    .lt(ShortUrlDO::getDeleteTime, cutoff)
                    .gt(ShortUrlDO::getId, sinceId)
                    .orderByAsc(ShortUrlDO::getId)
                    .last("LIMIT " + batchSize));
            if (batch.isEmpty()) {
                break;
            }
            purgeRecords(batch);
            purged += batch.size();
            sinceId = batch.get(batch.size() - 1).getId();
            if (batch.size() < batchSize) {
                break;
            }
        }
        if (purged > 0) {
            log.info("回收站自动清理完成: count={}, cutoff={}", purged, cutoff);
        }
        return purged;
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

    // ------------------------------------------------------------------ 内部实现

    private Mono<Void> updateStatusInternal(String code, int targetStatus, long userId, boolean admin,
                                            boolean toRecycleBin) {
        return Reactors.call(() -> {
            ShortUrlDO record = requireReadable(code, userId, admin);
            if (toRecycleBin && record.getStatus() == ShortLinkConstants.STATUS_DELETED) {
                throw new BizException(ErrorCode.LINK_NOT_IN_RECYCLE, "短链已在回收站中");
            }
            ShortUrlDO update = new ShortUrlDO();
            update.setId(record.getId());
            update.setStatus(targetStatus);
            if (toRecycleBin) {
                update.setDeleteTime(LocalDateTime.now());
            }
            update.setUpdateTime(LocalDateTime.now());
            shortUrlMapper.updateById(update);
            cache.evict(code);
            return null;
        }).then();
    }

    /**
     * 物理删除短链并清理缓存与统计（缓存键、累计 PV、归档行、当日活跃集合）。
     */
    private void purgeRecords(List<ShortUrlDO> records) {
        RBatch batch = redisson.createBatch();
        String today = LocalDate.now().format(DATE_FORMATTER);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FORMATTER);
        for (ShortUrlDO record : records) {
            String code = record.getShortCode();
            shortUrlMapper.delete(new LambdaQueryWrapper<ShortUrlDO>()
                    .eq(ShortUrlDO::getShortCode, code));
            statsMapper.delete(new LambdaQueryWrapper<ShortUrlStatsDO>()
                    .eq(ShortUrlStatsDO::getShortCode, code));
            batch.getBucket(ShortLinkKeys.shortUrlCache(code)).deleteAsync();
            batch.getAtomicLong(ShortLinkKeys.pvTotal(code)).deleteAsync();
            batch.getSet(ShortLinkKeys.codesOfDay(today)).removeAsync(code);
            batch.getSet(ShortLinkKeys.codesOfDay(yesterday)).removeAsync(code);
        }
        batch.execute();
    }

    private long resolveGroupId(Long groupId, long userId) {
        long resolved = groupId == null ? 0L : groupId;
        if (resolved != 0L) {
            requireGroupOwned(resolved, userId);
        }
        return resolved;
    }

    private void requireGroupOwned(long groupId, long userId) {
        LinkGroupDO group = linkGroupMapper.selectById(groupId);
        if (group == null || group.getUserId() != userId) {
            throw new BizException(ErrorCode.GROUP_NOT_FOUND, "分组不存在: " + groupId);
        }
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

    // ------------------------------------------------------------------ VO 组装

    private PageResult<ShortLinkVO> toPageResult(Page<ShortUrlDO> page) {
        Map<Long, String> groupNames = loadGroupNames(page.getRecords());
        Map<Long, String> domainPrefixes = loadDomainPrefixes(page.getRecords());
        String defaultPrefix = loadDefaultDomainPrefix();
        List<ShortLinkVO> records = page.getRecords().stream()
                .map(record -> toVO(record, groupNames.get(record.getGroupId()),
                        domainPrefix(record, domainPrefixes, defaultPrefix)))
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 域名前缀：域名记录缺失时回退默认域名前缀。
     */
    private String domainPrefix(ShortUrlDO record, Map<Long, String> domainPrefixes, String defaultPrefix) {
        String prefix = domainPrefixes.get(record.getDomainId());
        return prefix != null ? prefix : defaultPrefix;
    }

    /**
     * 默认域名前缀（兜底展示用，正常情况下域名表必有启用中的默认域名）。
     */
    private String loadDefaultDomainPrefix() {
        SurlDomainDO domain = domainService.defaultDomain();
        return domain == null ? "" : domain.getDomain();
    }

    private Map<Long, String> loadGroupNames(List<ShortUrlDO> records) {
        Set<Long> groupIds = records.stream()
                .map(ShortUrlDO::getGroupId)
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return linkGroupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(LinkGroupDO::getId, LinkGroupDO::getName, (a, b) -> a));
    }

    private Map<Long, String> loadDomainPrefixes(List<ShortUrlDO> records) {
        Set<Long> domainIds = records.stream()
                .map(ShortUrlDO::getDomainId)
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());
        if (domainIds.isEmpty()) {
            return Map.of();
        }
        return domainMapper.selectBatchIds(domainIds).stream()
                .collect(Collectors.toMap(SurlDomainDO::getId, SurlDomainDO::getDomain, (a, b) -> a));
    }

    private String groupNameOf(long groupId) {
        if (groupId == 0L) {
            return null;
        }
        LinkGroupDO group = linkGroupMapper.selectById(groupId);
        return group == null ? null : group.getName();
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

    private ShortLinkVO toVO(ShortUrlDO record, String groupName, String domainPrefix) {
        return new ShortLinkVO(record.getShortCode(), domainPrefix + "/" + record.getShortCode(),
                record.getLongUrl(), record.getTitle(), record.getStatus(),
                record.getGroupId(), groupName, record.getDomainId(), domainPrefix,
                record.getExpireTime(), record.getCreateTime());
    }

    // detail 组装参数载体
    private record DetailArgs(ShortUrlDO record, String groupName, String domainPrefix) {
    }

    private ShortLinkDetailVO toDetailVO(DetailArgs args, com.shortlink.common.dto.StatsVO stats) {
        ShortUrlDO record = args.record();
        return new ShortLinkDetailVO(record.getShortCode(), args.domainPrefix() + "/" + record.getShortCode(),
                record.getLongUrl(), record.getTitle(), record.getStatus(),
                record.getGroupId(), args.groupName(), record.getDomainId(), args.domainPrefix(),
                record.getExpireTime(), record.getCreateTime(), stats);
    }
}
