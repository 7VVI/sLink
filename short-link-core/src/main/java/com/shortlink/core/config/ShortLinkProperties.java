package com.shortlink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 短链系统业务配置项（前缀 shortlink）。
 */
@ConfigurationProperties(prefix = "shortlink")
public class ShortLinkProperties {

    private final IdGenerator idGenerator = new IdGenerator();

    private final Cache cache = new Cache();

    private final Bloom bloom = new Bloom();

    private final Stats stats = new Stats();

    private final Security security = new Security();

    private final RateLimit rateLimit = new RateLimit();

    private final RecycleBin recycleBin = new RecycleBin();

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    public Cache getCache() {
        return cache;
    }

    public Bloom getBloom() {
        return bloom;
    }

    public Stats getStats() {
        return stats;
    }

    public Security getSecurity() {
        return security;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public RecycleBin getRecycleBin() {
        return recycleBin;
    }

    /**
     * 发号器（Leaf-Segment 号段模式）配置。
     */
    public static class IdGenerator {

        /**
         * 发号业务标签，对应 leaf_alloc.biz_tag。
         */
        private String bizTag = "short_url";

        /**
         * 号段剩余量低于该比例时异步预加载下一段。
         */
        private double preloadThreshold = 0.2;

        /**
         * 号段耗尽时等待下一段就绪的最长时间。
         */
        private Duration nextSegmentWaitTimeout = Duration.ofSeconds(5);

        public String getBizTag() {
            return bizTag;
        }

        public void setBizTag(String bizTag) {
            this.bizTag = bizTag;
        }

        public double getPreloadThreshold() {
            return preloadThreshold;
        }

        public void setPreloadThreshold(double preloadThreshold) {
            this.preloadThreshold = preloadThreshold;
        }

        public Duration getNextSegmentWaitTimeout() {
            return nextSegmentWaitTimeout;
        }

        public void setNextSegmentWaitTimeout(Duration nextSegmentWaitTimeout) {
            this.nextSegmentWaitTimeout = nextSegmentWaitTimeout;
        }
    }

    /**
     * 三级缓存配置。
     */
    public static class Cache {

        /**
         * L1 Caffeine 最大条目数。
         */
        private long l1MaximumSize = 50_000;

        /**
         * L1 写后过期时间。
         */
        private Duration l1ExpireAfterWrite = Duration.ofSeconds(30);

        /**
         * L2 Redis 过期时间（内部会附加 ±10% 随机抖动防雪崩）。
         */
        private Duration l2Expire = Duration.ofHours(24);

        /**
         * 空值缓存（防穿透）过期时间。
         */
        private Duration negativeExpire = Duration.ofSeconds(60);

        public long getL1MaximumSize() {
            return l1MaximumSize;
        }

        public void setL1MaximumSize(long l1MaximumSize) {
            this.l1MaximumSize = l1MaximumSize;
        }

        public Duration getL1ExpireAfterWrite() {
            return l1ExpireAfterWrite;
        }

        public void setL1ExpireAfterWrite(Duration l1ExpireAfterWrite) {
            this.l1ExpireAfterWrite = l1ExpireAfterWrite;
        }

        public Duration getL2Expire() {
            return l2Expire;
        }

        public void setL2Expire(Duration l2Expire) {
            this.l2Expire = l2Expire;
        }

        public Duration getNegativeExpire() {
            return negativeExpire;
        }

        public void setNegativeExpire(Duration negativeExpire) {
            this.negativeExpire = negativeExpire;
        }
    }

    /**
     * 布隆过滤器配置。
     */
    public static class Bloom {

        /**
         * 预期插入量（创建短链总量规模）。
         */
        private long expectedInsertions = 10_000_000L;

        /**
         * 误判率。
         */
        private double falsePositiveRate = 0.001;

        /**
         * 是否在启动时全量重建（多实例滚动发布期间建议保持 true）。
         */
        private boolean rebuildOnStartup = true;

        public long getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(long expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFalsePositiveRate() {
            return falsePositiveRate;
        }

        public void setFalsePositiveRate(double falsePositiveRate) {
            this.falsePositiveRate = falsePositiveRate;
        }

        public boolean isRebuildOnStartup() {
            return rebuildOnStartup;
        }

        public void setRebuildOnStartup(boolean rebuildOnStartup) {
            this.rebuildOnStartup = rebuildOnStartup;
        }
    }

    /**
     * 统计模块配置。
     */
    public static class Stats {

        /**
         * Disruptor RingBuffer 大小，必须为 2 的幂。
         */
        private int ringBufferSize = 1 << 16;

        /**
         * 批量刷写阈值（条数）。
         */
        private int batchSize = 256;

        /**
         * 批量刷写时间窗口。
         */
        private Duration flushInterval = Duration.ofMillis(200);

        /**
         * 归档时每次读取短码的批大小。
         */
        private int archiveBatchSize = 500;

        public int getRingBufferSize() {
            return ringBufferSize;
        }

        public void setRingBufferSize(int ringBufferSize) {
            this.ringBufferSize = ringBufferSize;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }

        public int getArchiveBatchSize() {
            return archiveBatchSize;
        }

        public void setArchiveBatchSize(int archiveBatchSize) {
            this.archiveBatchSize = archiveBatchSize;
        }
    }

    /**
     * 安全配置。
     */
    public static class Security {

        /**
         * 恶意域名黑名单。
         */
        private List<String> blacklistDomains = new ArrayList<>();

        public List<String> getBlacklistDomains() {
            return blacklistDomains;
        }

        public void setBlacklistDomains(List<String> blacklistDomains) {
            this.blacklistDomains = blacklistDomains;
        }
    }

    /**
     * 限流配置。
     */
    public static class RateLimit {

        /**
         * 是否开启按 IP 限流。
         */
        private boolean enabled = true;

        /**
         * 单 IP 每秒允许的请求数（令牌桶）。
         */
        private long perIpQps = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPerIpQps() {
            return perIpQps;
        }

        public void setPerIpQps(long perIpQps) {
            this.perIpQps = perIpQps;
        }
    }

    /**
     * 回收站配置。
     */
    public static class RecycleBin {

        /**
         * 回收站保留天数，到期自动物理清除。
         */
        private int retentionDays = 30;

        /**
         * 自动清除任务的 cron 表达式（默认每天 03:30）。
         */
        private String purgeCron = "0 30 3 * * ?";

        /**
         * 自动清除每批处理的短链数。
         */
        private int purgeBatchSize = 500;

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getPurgeCron() {
            return purgeCron;
        }

        public void setPurgeCron(String purgeCron) {
            this.purgeCron = purgeCron;
        }

        public int getPurgeBatchSize() {
            return purgeBatchSize;
        }

        public void setPurgeBatchSize(int purgeBatchSize) {
            this.purgeBatchSize = purgeBatchSize;
        }
    }
}
