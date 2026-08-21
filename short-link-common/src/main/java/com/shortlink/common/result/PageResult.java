package com.shortlink.common.result;

import java.util.List;

/**
 * 分页返回体。
 *
 * @param <T> 记录类型
 */
public record PageResult<T>(long total, long size, long current, long pages, List<T> records) {

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(total, size, current, pages, records);
    }
}
