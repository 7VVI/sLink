package com.shortlink.common.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 上下线短链请求。
 */
public record UpdateStatusReq(@NotNull(message = "enabled 不能为空") Boolean enabled) {
}
