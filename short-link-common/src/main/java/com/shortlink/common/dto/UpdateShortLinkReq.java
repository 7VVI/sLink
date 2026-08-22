package com.shortlink.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑短链请求（groupId 为 null 表示保持原分组不变）。
 */
public record UpdateShortLinkReq(

        @NotBlank(message = "长链接不能为空")
        @Size(max = 2048, message = "长链接长度不能超过 2048")
        String longUrl,

        @Size(max = 128, message = "标题长度不能超过 128")
        String title,

        @Min(value = 0, message = "groupId 不合法")
        Long groupId
) {
}
