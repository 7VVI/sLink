package com.shortlink.server.controller;

import com.shortlink.common.dto.DomainVO;
import com.shortlink.common.result.Result;
import com.shortlink.core.service.DomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 域名查询接口（登录用户可选域名列表，管理接口见 AdminController）。
 */
@Tag(name = "域名")
@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final DomainService domainService;

    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }

    @Operation(summary = "可用域名列表（仅启用，默认域名排前）")
    @GetMapping
    public Mono<Result<List<DomainVO>>> listEnabled() {
        return domainService.listEnabled().map(Result::ok);
    }
}
