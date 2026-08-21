package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 分组信息（linkCount 为该分组下未删除短链数）。
 */
public record GroupVO(Long id, String name, long linkCount, LocalDateTime createTime) {
}
