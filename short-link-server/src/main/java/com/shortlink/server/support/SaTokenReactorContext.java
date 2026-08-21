package com.shortlink.server.support;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Supplier;

/**
 * Sa-Token WebFlux 上下文工具。
 *
 * <p>Sa-Token 1.39 的 SaReactorFilter 仅在其鉴权回调内绑定 ThreadLocal 上下文，
 * 控制器方法体与响应式 lambda 中调用 StpUtil 需手动绑定当前 exchange。</p>
 */
public final class SaTokenReactorContext {

    private SaTokenReactorContext() {
    }

    /**
     * 在指定 exchange 上下文中执行 StpUtil 相关同步调用。
     */
    public static <T> T using(ServerWebExchange exchange, Supplier<T> action) {
        SaReactorSyncHolder.setContext(exchange);
        try {
            return action.get();
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    /**
     * 在指定 exchange 上下文中执行无返回值的 StpUtil 调用。
     */
    public static void using(ServerWebExchange exchange, Runnable action) {
        SaReactorSyncHolder.setContext(exchange);
        try {
            action.run();
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }
}
