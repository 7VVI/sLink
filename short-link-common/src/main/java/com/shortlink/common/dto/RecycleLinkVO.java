package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 回收站短链信息（purgeTime 为自动清除时间 = deleteTime + 保留天数）。
 */
public record RecycleLinkVO(
        String code,
        String shortUrl,
        String longUrl,
        String title,
        String groupName,
        LocalDateTime deleteTime,
        LocalDateTime purgeTime
) {
}
