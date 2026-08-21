package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 短链信息。
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
        LocalDateTime createTime
) {
}
