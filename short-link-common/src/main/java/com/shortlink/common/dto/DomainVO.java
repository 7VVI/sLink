package com.shortlink.common.dto;

import java.time.LocalDateTime;

/**
 * 域名信息。
 */
public record DomainVO(Long id, String domain, String name, boolean isDefault, Integer status,
                       LocalDateTime createTime) {
}
