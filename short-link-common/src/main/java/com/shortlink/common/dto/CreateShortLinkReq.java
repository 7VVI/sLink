package com.shortlink.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建短链请求。
 */
public record CreateShortLinkReq(

        @NotBlank(message = "长链接不能为空")
        @Size(max = 2048, message = "长链接长度不能超过 2048")
        String longUrl,

        @Size(max = 128, message = "标题长度不能超过 128")
        String title,

        @Min(value = 1, message = "有效期天数至少为 1")
        @Max(value = 3650, message = "有效期天数不能超过 3650")
        Integer expireDays
) {
}
