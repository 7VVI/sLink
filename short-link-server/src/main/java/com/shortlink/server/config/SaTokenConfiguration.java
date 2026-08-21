package com.shortlink.server.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.common.result.Result;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 鉴权配置。
 *
 * <p>路由规则：/api/** 需登录，/api/admin/** 需要 ADMIN 角色；
 * 跳转 /{code}、登录注册、OpenAPI 文档、Actuator 为公开路径。</p>
 */
@Configuration
public class SaTokenConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SaTokenConfiguration.class);

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/api/**")
                .addExclude("/api/auth/login", "/api/auth/register")
                .setAuth(obj -> {
                    SaRouter.match("/api/admin/**", r -> StpUtil.checkRole(ShortLinkConstants.ROLE_ADMIN));
                    SaRouter.match("/api/**", r -> StpUtil.checkLogin());
                })
                .setError(e -> {
                    if (e instanceof NotLoginException) {
                        return Result.fail(ErrorCode.UNAUTHORIZED);
                    }
                    if (e instanceof NotRoleException || e instanceof NotPermissionException) {
                        return Result.fail(ErrorCode.FORBIDDEN);
                    }
                    log.warn("认证过滤器异常", e);
                    return Result.fail(ErrorCode.SYSTEM_ERROR, e.getMessage());
                });
    }

    @Bean
    public SaTokenDao saTokenDao(RedissonClient redissonClient) {
        return new RedissonSaTokenDao(redissonClient);
    }
}
