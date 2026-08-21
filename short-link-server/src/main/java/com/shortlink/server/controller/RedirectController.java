package com.shortlink.server.controller;

import com.shortlink.core.cache.ShortUrlCacheValue;
import com.shortlink.core.service.ShortLinkService;
import com.shortlink.core.stats.StatsCollector;
import com.shortlink.server.support.WebSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 短链跳转接口（公开访问，热路径）。
 *
 * <p>302 跳转 + 异步统计；命中本地缓存时全程无网络 IO。</p>
 */
@Tag(name = "跳转")
@RestController
public class RedirectController {

    private final ShortLinkService shortLinkService;

    private final StatsCollector statsCollector;

    public RedirectController(ShortLinkService shortLinkService, StatsCollector statsCollector) {
        this.shortLinkService = shortLinkService;
        this.statsCollector = statsCollector;
    }

    @Operation(summary = "短码跳转")
    @GetMapping("/{code}")
    public Mono<ResponseEntity<Void>> redirect(@PathVariable("code") String code, ServerHttpRequest request) {
        return shortLinkService.resolve(code)
                .flatMap(Mono::justOrEmpty)
                .map(value -> {
                    // RingBuffer 发布为纯内存操作，不阻塞跳转响应
                    statsCollector.publish(code, WebSupport.visitorId(request));
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, value.getLongUrl())
                            .cacheControl(CacheControl.noStore())
                            .<Void>build();
                })
                .defaultIfEmpty(ResponseEntity.notFound().<Void>build());
    }
}
