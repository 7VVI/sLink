package com.shortlink.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterReq(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名仅支持 3-32 位字母、数字、下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
        String password,

        @Size(max = 32, message = "昵称长度不能超过 32")
        String nickname
) {
}
