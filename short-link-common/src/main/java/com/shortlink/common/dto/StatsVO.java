package com.shortlink.common.dto;

/**
 * 实时统计：当日 PV / 当日 UV / 累计 PV / 累计 UV。
 *
 * <p>累计 UV = 归档表 stat_date &lt; 今日 的 SUM(uv) + 当日 HLL 计数（归档按天覆盖写，今日行不完整故不计入）。</p>
 */
public record StatsVO(String code, long todayPv, long todayUv, long totalPv, long totalUv) {
}
