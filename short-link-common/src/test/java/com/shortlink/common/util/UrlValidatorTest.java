package com.shortlink.common.util;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlValidatorTest {

    private final List<String> blacklist = List.of("malicious.example.com", "bad.test");

    @Test
    void shouldAcceptHttpAndHttps() {
        assertDoesNotThrow(() -> UrlValidator.requireValid("https://example.com/a?b=1", blacklist));
        assertDoesNotThrow(() -> UrlValidator.requireValid("http://example.com", blacklist));
        assertDoesNotThrow(() -> UrlValidator.requireValid("HTTPS://EXAMPLE.COM/path", blacklist));
    }

    @Test
    void shouldRejectNonHttpScheme() {
        BizException e = assertThrows(BizException.class,
                () -> UrlValidator.requireValid("javascript:alert(1)", blacklist));
        assertEquals(ErrorCode.URL_INVALID, e.getErrorCode());
    }

    @Test
    void shouldRejectMissingHost() {
        assertThrows(BizException.class, () -> UrlValidator.requireValid("http:///path", blacklist));
        assertThrows(BizException.class, () -> UrlValidator.requireValid("   ", blacklist));
    }

    @Test
    void shouldRejectBlacklistedDomainIncludingSubDomain() {
        assertEquals(ErrorCode.URL_BLACKLISTED,
                assertThrows(BizException.class,
                        () -> UrlValidator.requireValid("https://malicious.example.com/x", blacklist)).getErrorCode());
        assertEquals(ErrorCode.URL_BLACKLISTED,
                assertThrows(BizException.class,
                        () -> UrlValidator.requireValid("https://a.b.bad.test/x", blacklist)).getErrorCode());
    }

    @Test
    void shouldRejectTooLongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(2100);
        assertThrows(BizException.class, () -> UrlValidator.requireValid(longUrl, blacklist));
    }

    @Test
    void shouldAcceptValidDomainPrefix() {
        assertDoesNotThrow(() -> UrlValidator.requireValidDomainPrefix("https://s.cn"));
        assertDoesNotThrow(() -> UrlValidator.requireValidDomainPrefix("http://localhost:8080"));
        assertDoesNotThrow(() -> UrlValidator.requireValidDomainPrefix("https://s.cn/"));
    }

    @Test
    void shouldRejectInvalidDomainPrefix() {
        assertEquals(ErrorCode.DOMAIN_INVALID,
                assertThrows(BizException.class,
                        () -> UrlValidator.requireValidDomainPrefix("s.cn")).getErrorCode());
        assertThrows(BizException.class,
                () -> UrlValidator.requireValidDomainPrefix("https://s.cn/path"));
        assertThrows(BizException.class,
                () -> UrlValidator.requireValidDomainPrefix("https://s.cn?a=1"));
        assertThrows(BizException.class,
                () -> UrlValidator.requireValidDomainPrefix("ftp://s.cn"));
        assertThrows(BizException.class,
                () -> UrlValidator.requireValidDomainPrefix(" "));
    }
}
