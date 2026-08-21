package com.shortlink.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.dto.CreateGroupReq;
import com.shortlink.common.dto.GroupVO;
import com.shortlink.common.dto.UpdateGroupReq;
import com.shortlink.common.result.Result;
import com.shortlink.core.service.GroupService;
import com.shortlink.server.support.SaTokenReactorContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 分组管理接口（需登录，仅操作本人分组）。
 */
@Tag(name = "分组")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(summary = "创建分组")
    @PostMapping
    public Mono<Result<GroupVO>> create(@Valid @RequestBody CreateGroupReq request, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return groupService.create(request, userId).map(Result::ok);
    }

    @Operation(summary = "分组列表（含各分组短链数）")
    @GetMapping
    public Mono<Result<List<GroupVO>>> list(ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return groupService.list(userId).map(Result::ok);
    }

    @Operation(summary = "重命名分组")
    @PutMapping("/{id}")
    public Mono<Result<Void>> rename(@PathVariable("id") Long id,
                                     @Valid @RequestBody UpdateGroupReq request,
                                     ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return groupService.rename(id, request.name(), userId).map(v -> Result.ok());
    }

    @Operation(summary = "删除分组（组内短链移回未分组）")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> remove(@PathVariable("id") Long id, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return groupService.remove(id, userId).map(v -> Result.ok());
    }
}
