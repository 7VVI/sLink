package com.shortlink.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增域名请求。
 */
public record CreateDomainReq(

        @NotBlank(message = "域名不能为空")
        @Size(max = 255, message = "域名长度不能超过 255")
        String domain,

        @Size(max = 64, message = "备注名长度不能超过 64")
        String name,

        Boolean isDefault
) {
}
