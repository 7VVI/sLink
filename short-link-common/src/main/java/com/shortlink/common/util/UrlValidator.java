package com.shortlink.common.util;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;

/**
 * 长链接合法性校验：仅允许 http/https，且域名不得命中黑名单。
 */
public final class UrlValidator {

    /**
     * 数据库 long_url 列长度上限。
     */
    public static final int MAX_URL_LENGTH = 2048;

    private UrlValidator() {
    }

    /**
     * 校验长链接，不合法时抛出业务异常。
     *
     * @param url               待创建的长链接
     * @param blacklistDomains  恶意域名黑名单（支持匹配子域名）
     */
    public static void requireValid(String url, Collection<String> blacklistDomains) {
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.URL_INVALID, "长链接不能为空");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new BizException(ErrorCode.URL_INVALID, "长链接长度不能超过 " + MAX_URL_LENGTH);
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.URL_INVALID, "长链接格式不合法");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BizException(ErrorCode.URL_INVALID, "仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException(ErrorCode.URL_INVALID, "长链接缺少主机名");
        }
        requireNotBlacklisted(host, blacklistDomains);
    }

    private static void requireNotBlacklisted(String host, Collection<String> blacklistDomains) {
        if (blacklistDomains == null || blacklistDomains.isEmpty()) {
            return;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String raw : blacklistDomains) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String domain = raw.trim().toLowerCase(Locale.ROOT);
            if (normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain)) {
                throw new BizException(ErrorCode.URL_BLACKLISTED, "长链接域名命中黑名单: " + domain);
            }
        }
    }
}
