package com.shortlink.common.dto;

import java.time.LocalDate;

/**
 * 按日归档统计。
 */
public record StatsHistoryVO(LocalDate statDate, long pv, long uv) {
}
