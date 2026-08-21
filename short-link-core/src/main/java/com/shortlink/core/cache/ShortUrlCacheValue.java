package com.shortlink.core.cache;

import java.io.Serializable;

/**
 * 短链缓存值（L1/L2 共用，Kryo 序列化）。
 *
 * <p>{@link #EMPTY} 为空值哨兵：布隆过滤器误判或 DB 未命中时写入短 TTL 空值，防止缓存穿透。</p>
 */
public class ShortUrlCacheValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 空值哨兵，代表“确认不存在”。
     */
    public static final ShortUrlCacheValue EMPTY = new ShortUrlCacheValue(null, 0, 0, true);

    private String longUrl;

    private int status;

    /**
     * 过期时间戳（毫秒），0 表示永不过期。
     */
    private long expireAtMillis;

    private boolean negative;

    public ShortUrlCacheValue() {
    }

    private ShortUrlCacheValue(String longUrl, int status, long expireAtMillis, boolean negative) {
        this.longUrl = longUrl;
        this.status = status;
        this.expireAtMillis = expireAtMillis;
        this.negative = negative;
    }

    public static ShortUrlCacheValue of(String longUrl, int status, long expireAtMillis) {
        return new ShortUrlCacheValue(longUrl, status, expireAtMillis, false);
    }

    /**
     * 当前是否有效：未删除/下线且未过期。
     */
    public boolean isEffectiveNow() {
        return !negative
                && status == com.shortlink.common.constant.ShortLinkConstants.STATUS_ENABLED
                && (expireAtMillis <= 0 || expireAtMillis > System.currentTimeMillis());
    }

    public boolean isNegative() {
        return negative;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getExpireAtMillis() {
        return expireAtMillis;
    }

    public void setExpireAtMillis(long expireAtMillis) {
        this.expireAtMillis = expireAtMillis;
    }

    public void setNegative(boolean negative) {
        this.negative = negative;
    }
}
