package com.shortlink.common.dto;

/**
 * 实时统计：当日 PV / 当日 UV / 累计 PV。
 */
public record StatsVO(String code, long todayPv, long todayUv, long totalPv) {
}
