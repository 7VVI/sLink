package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 短链信息（含列表页实时统计：今日/累计 PV、今日/累计 UV）。
 */
public record ShortLinkVO(
        String code,
        String shortUrl,
        String longUrl,
        String title,
        Integer status,
        Long groupId,
        String groupName,
        Long domainId,
        String domain,
        LocalDateTime expireTime,
        LocalDateTime createTime,
        long todayPv,
        long todayUv,
        long totalPv,
        long totalUv
) {
}
