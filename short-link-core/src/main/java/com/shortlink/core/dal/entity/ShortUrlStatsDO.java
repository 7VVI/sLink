package com.shortlink.core.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 按日统计归档记录（主库 short_link_main.short_url_stats，联合主键 short_code + stat_date）。
 */
@TableName("short_url_stats")
public class ShortUrlStatsDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String shortCode;

    private LocalDate statDate;

    private Long pv;

    private Long uv;

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Long getPv() {
        return pv;
    }

    public void setPv(Long pv) {
        this.pv = pv;
    }

    public Long getUv() {
        return uv;
    }

    public void setUv(Long uv) {
        this.uv = uv;
    }
}
