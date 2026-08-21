package com.shortlink.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.CreateShortLinkReq;
import com.shortlink.common.dto.ShortLinkDetailVO;
import com.shortlink.common.dto.ShortLinkVO;
import com.shortlink.common.dto.UpdateStatusReq;
import com.shortlink.common.result.PageResult;
import com.shortlink.common.result.Result;
import com.shortlink.core.service.ShortLinkService;
import com.shortlink.server.support.SaTokenReactorContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 短链管理接口（需登录）。
 */
@Tag(name = "短链管理")
@Validated
@RestController
@RequestMapping("/api/short-links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @Operation(summary = "创建短链")
    @PostMapping
    public Mono<Result<ShortLinkVO>> create(@Valid @RequestBody CreateShortLinkReq request,
                                            ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return shortLinkService.create(request, userId).map(Result::ok);
    }

    @Operation(summary = "我的短链分页列表")
    @GetMapping
    public Mono<Result<PageResult<ShortLinkVO>>> page(
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return shortLinkService.pageByUser(userId, pageNo, pageSize).map(Result::ok);
    }

    @Operation(summary = "短链详情（含实时统计）")
    @GetMapping("/{code}")
    public Mono<Result<ShortLinkDetailVO>> detail(@PathVariable("code") String code, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.detail(code, userId, admin).map(Result::ok);
    }

    @Operation(summary = "上线/下线短链（下线秒级生效）")
    @PutMapping("/{code}/status")
    public Mono<Result<Void>> changeStatus(@PathVariable("code") String code,
                                           @Valid @RequestBody UpdateStatusReq request,
                                           ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.changeStatus(code, request.enabled(), userId, admin)
                .map(v -> Result.ok());
    }

    @Operation(summary = "删除短链（逻辑删除）")
    @DeleteMapping("/{code}")
    public Mono<Result<Void>> remove(@PathVariable("code") String code, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.remove(code, userId, admin).map(v -> Result.ok());
    }
}
