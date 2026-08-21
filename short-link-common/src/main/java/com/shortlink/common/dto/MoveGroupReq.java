package com.shortlink.common.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 移动短链到分组请求（groupId = 0 表示移回未分组）。
 */
public record MoveGroupReq(@NotNull(message = "groupId 不能为空") Long groupId) {
}
