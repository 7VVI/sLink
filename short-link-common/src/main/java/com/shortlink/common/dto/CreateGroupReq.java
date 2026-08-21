package com.shortlink.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建分组请求。
 */
public record CreateGroupReq(

        @NotBlank(message = "分组名不能为空")
        @Size(max = 32, message = "分组名长度不能超过 32")
        String name
) {
}
