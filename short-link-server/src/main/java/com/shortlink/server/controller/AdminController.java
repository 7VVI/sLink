package com.shortlink.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.dto.CreateDomainReq;
import com.shortlink.common.dto.DomainVO;
import com.shortlink.common.dto.ShortLinkVO;
import com.shortlink.common.dto.UpdateStatusReq;
import com.shortlink.common.result.PageResult;
import com.shortlink.common.result.Result;
import com.shortlink.core.service.DomainService;
import com.shortlink.core.service.ShortLinkService;
import com.shortlink.core.support.Reactors;
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

import java.util.List;

/**
 * 管理端接口（需 ADMIN 角色，路由级拦截见 SaTokenConfiguration）。
 */
@Tag(name = "管理端")
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ShortLinkService shortLinkService;

    private final DomainService domainService;

    public AdminController(ShortLinkService shortLinkService, DomainService domainService) {
        this.shortLinkService = shortLinkService;
        this.domainService = domainService;
    }

    // ------------------------------------------------------------------ 短链管理

    @Operation(summary = "全部短链分页列表（可按状态过滤，2=回收站）")
    @GetMapping("/short-links")
    public Mono<Result<PageResult<ShortLinkVO>>> page(
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Integer status) {
        return shortLinkService.pageAll(pageNo, pageSize, status).map(Result::ok);
    }

    @Operation(summary = "强制上线/下线任意短链")
    @PutMapping("/short-links/{code}/status")
    public Mono<Result<Void>> changeStatus(@PathVariable("code") String code,
                                           @Valid @RequestBody UpdateStatusReq request,
                                           ServerWebExchange exchange) {
        long adminId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return shortLinkService.changeStatus(code, request.enabled(), adminId, true)
                .map(v -> Result.ok());
    }

    @Operation(summary = "删除任意短链（移入回收站）")
    @DeleteMapping("/short-links/{code}")
    public Mono<Result<Void>> remove(@PathVariable("code") String code, ServerWebExchange exchange) {
        long adminId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return shortLinkService.remove(code, adminId, true).map(v -> Result.ok());
    }

    @Operation(summary = "手动触发回收站清理（清理超期短链，返回清理条数）")
    @PostMapping("/recycle-bin/purge")
    public Mono<Result<Long>> purgeRecycleBin() {
        return Reactors.call(shortLinkService::purgeExpired).map(Result::ok);
    }

    // ------------------------------------------------------------------ 域名管理

    @Operation(summary = "域名列表（全部）")
    @GetMapping("/domains")
    public Mono<Result<List<DomainVO>>> listDomains() {
        return domainService.listAll().map(Result::ok);
    }

    @Operation(summary = "新增域名（isDefault=true 同时设为默认）")
    @PostMapping("/domains")
    public Mono<Result<DomainVO>> createDomain(@Valid @RequestBody CreateDomainReq request) {
        return domainService.create(request).map(Result::ok);
    }

    @Operation(summary = "上线/下线域名（默认域名不允许下线）")
    @PutMapping("/domains/{id}/status")
    public Mono<Result<Void>> changeDomainStatus(@PathVariable("id") Long id,
                                                 @Valid @RequestBody UpdateStatusReq request) {
        return domainService.changeStatus(id, request.enabled()).map(v -> Result.ok());
    }

    @Operation(summary = "设为默认域名")
    @PutMapping("/domains/{id}/default")
    public Mono<Result<Void>> setDefaultDomain(@PathVariable("id") Long id) {
        return domainService.setDefault(id).map(v -> Result.ok());
    }

    @Operation(summary = "删除域名（默认域名不允许删除）")
    @DeleteMapping("/domains/{id}")
    public Mono<Result<Void>> removeDomain(@PathVariable("id") Long id) {
        return domainService.remove(id).map(v -> Result.ok());
    }
}
