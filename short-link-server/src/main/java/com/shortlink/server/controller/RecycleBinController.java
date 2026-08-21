package com.shortlink.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.RecycleLinkVO;
import com.shortlink.common.result.PageResult;
import com.shortlink.common.result.Result;
import com.shortlink.core.service.ShortLinkService;
import com.shortlink.server.support.SaTokenReactorContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 回收站接口：删除的短链默认保留 30 天（可配置），到期自动物理清除。
 */
@Tag(name = "回收站")
@Validated
@RestController
@RequestMapping("/api/recycle-bin")
public class RecycleBinController {

    private final ShortLinkService shortLinkService;

    public RecycleBinController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @Operation(summary = "回收站分页列表（按移入时间倒序，含自动清除时间）")
    @GetMapping
    public Mono<Result<PageResult<RecycleLinkVO>>> page(
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return shortLinkService.pageRecycled(userId, pageNo, pageSize).map(Result::ok);
    }

    @Operation(summary = "从回收站还原短链")
    @PutMapping("/{code}/restore")
    public Mono<Result<Void>> restore(@PathVariable("code") String code, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.restore(code, userId, admin).map(v -> Result.ok());
    }

    @Operation(summary = "彻底删除回收站短链（不可恢复）")
    @DeleteMapping("/{code}")
    public Mono<Result<Void>> permanentDelete(@PathVariable("code") String code, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.permanentDelete(code, userId, admin).map(v -> Result.ok());
    }
}
