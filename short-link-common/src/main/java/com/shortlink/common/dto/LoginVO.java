package com.shortlink.common.dto;

/**
 * 登录成功返回：token 由前端放入 Header {@code shortLinkToken} 携带。
 */
public record LoginVO(String tokenName, String tokenValue, Long userId, String nickname, String role) {
}
