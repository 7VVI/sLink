package com.shortlink.core.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.core.dal.entity.ShortUrlDO;

/**
 * 短链 Mapper。查询条件含 short_code 时可精确路由到单分片；
 * 仅按 user_id 等条件查询时广播到全部分片后合并。
 */
public interface ShortUrlMapper extends BaseMapper<ShortUrlDO> {
}
