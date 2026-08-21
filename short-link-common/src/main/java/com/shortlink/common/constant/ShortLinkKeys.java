package com.shortlink.common.constant;

/**
 * Redis Key 规划（统一前缀 "s:"）。
 *
 * <table border="1">
 *   <tr><th>Key</th><th>类型</th><th>TTL</th><th>说明</th></tr>
 *   <tr><td>s:url:{code}</td><td>String(Kryo)</td><td>24h ± 抖动</td><td>短链 L2 缓存</td></tr>
 *   <tr><td>s:bloom:short_url</td><td>BloomFilter</td><td>永不过期</td><td>存在性布隆过滤器</td></tr>
 *   <tr><td>s:pv:{code}:{yyyyMMdd}</td><td>String</td><td>48h</td><td>当日 PV</td></tr>
 *   <tr><td>s:uv:{code}:{yyyyMMdd}</td><td>HyperLogLog</td><td>48h</td><td>当日 UV</td></tr>
 *   <tr><td>s:pv:t:{code}</td><td>String</td><td>永不过期</td><td>累计 PV</td></tr>
 *   <tr><td>s:codes:{yyyyMMdd}</td><td>Set</td><td>49h</td><td>当日有点击的短码集合（归档用）</td></tr>
 *   <tr><td>s:rl:{ip}</td><td>RateLimiter</td><td>-</td><td>按 IP 令牌桶限流</td></tr>
 * </table>
 */
public final class ShortLinkKeys {

    private static final String PREFIX = "s:";

    private static final String CACHE_KEY = PREFIX + "url:";

    private static final String BLOOM_KEY = PREFIX + "bloom:short_url";

    private static final String PV_KEY = PREFIX + "pv:";

    private static final String UV_KEY = PREFIX + "uv:";

    private static final String PV_TOTAL_KEY = PREFIX + "pv:t:";

    private static final String CODES_OF_DAY_KEY = PREFIX + "codes:";

    private static final String RATE_LIMIT_KEY = PREFIX + "rl:";

    private ShortLinkKeys() {
    }

    public static String shortUrlCache(String code) {
        return CACHE_KEY + code;
    }

    public static String bloomFilter() {
        return BLOOM_KEY;
    }

    public static String pvOfDay(String code, String date) {
        return PV_KEY + code + ":" + date;
    }

    public static String uvOfDay(String code, String date) {
        return UV_KEY + code + ":" + date;
    }

    public static String pvTotal(String code) {
        return PV_TOTAL_KEY + code;
    }

    public static String codesOfDay(String date) {
        return CODES_OF_DAY_KEY + date;
    }

    public static String rateLimit(String clientIp) {
        return RATE_LIMIT_KEY + clientIp;
    }
}
