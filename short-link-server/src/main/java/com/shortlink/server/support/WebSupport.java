package com.shortlink.server.support;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

/**
 * Web 层工具：客户端 IP 提取与访客标识生成。
 */
public final class WebSupport {

    private WebSupport() {
    }

    /**
     * 客户端 IP：优先取反向代理注入的 X-Forwarded-For 首个地址。
     */
    public static String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String ip = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }

    /**
     * 访客标识：IP + User-Agent 指纹，用于 HyperLogLog 去重（UV）。
     */
    public static String visitorId(ServerHttpRequest request) {
        String ip = clientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String raw = ip + "|" + (userAgent == null ? "" : userAgent);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
