package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 短链详情（附带实时统计）。
 */
public record ShortLinkDetailVO(
        String code,
        String shortUrl,
        String longUrl,
        String title,
        Integer status,
        LocalDateTime expireTime,
        LocalDateTime createTime,
        StatsVO stats
) {
}
