package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 用户信息。
 */
public record UserInfoVO(Long userId, String username, String nickname, String role, LocalDateTime createTime) {
}
