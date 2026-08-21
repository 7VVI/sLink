package com.shortlink.core.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.core.dal.entity.ShortUrlStatsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计归档 Mapper。
 */
public interface ShortUrlStatsMapper extends BaseMapper<ShortUrlStatsDO> {

    /**
     * 幂等 upsert：按 (short_code, stat_date) 覆盖写，重复归档安全。
     */
    @Insert("INSERT INTO short_url_stats (short_code, stat_date, pv, uv) "
            + "VALUES (#{shortCode}, #{statDate}, #{pv}, #{uv}) "
            + "ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv)")
    int upsert(ShortUrlStatsDO stats);

    @Select("SELECT short_code, stat_date, pv, uv FROM short_url_stats "
            + "WHERE short_code = #{code} AND stat_date >= #{since} ORDER BY stat_date DESC")
    List<ShortUrlStatsDO> selectHistory(@Param("code") String code, @Param("since") LocalDate since);
}
