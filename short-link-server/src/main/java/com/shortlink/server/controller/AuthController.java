package com.shortlink.server.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.LoginReq;
import com.shortlink.common.dto.LoginVO;
import com.shortlink.common.dto.RegisterReq;
import com.shortlink.common.dto.UserInfoVO;
import com.shortlink.common.result.Result;
import com.shortlink.core.dal.entity.UserDO;
import com.shortlink.core.service.UserService;
import com.shortlink.server.support.SaTokenReactorContext;
import com.shortlink.server.support.WebSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 登录鉴权接口（Sa-Token）。
 *
 * <p>登录后角色写入会话缓存；token 通过响应体下发，前端后续置于
 * Header {@code shortLinkToken}。Sa-Token 1.39 的 WebFlux 上下文为
 * ThreadLocal 模型，所有 StpUtil 调用需经 {@link SaTokenReactorContext} 绑定。</p>
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Mono<Result<Long>> register(@Valid @RequestBody RegisterReq request) {
        return userService.register(request).map(Result::ok);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Mono<Result<LoginVO>> login(@Valid @RequestBody LoginReq request, ServerWebExchange exchange) {
        String clientIp = WebSupport.clientIp(exchange.getRequest());
        return userService.verifyLogin(request)
                .map(user -> SaTokenReactorContext.using(exchange, () -> {
                    StpUtil.login(user.getId(), clientIp);
                    StpUtil.getSession().set(ShortLinkConstants.SESSION_ROLE, userService.roleOf(user));
                    return toLoginVO(user);
                }))
                .map(Result::ok);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(ServerWebExchange exchange) {
        SaTokenReactorContext.using(exchange, () -> {
            StpUtil.logout();
            return null;
        });
        return Result.ok();
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Mono<Result<UserInfoVO>> me(ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        return userService.userInfo(userId).map(Result::ok);
    }

    private LoginVO toLoginVO(UserDO user) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginVO(tokenInfo.getTokenName(), tokenInfo.getTokenValue(),
                user.getId(), user.getNickname(), userService.roleOf(user));
    }
}
