package com.shortlink.core.support;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 响应式工具：将阻塞调用（JDBC、同步 Redis）调度到 boundedElastic，
 * 避免阻塞 WebFlux 事件循环线程。
 */
public final class Reactors {

    private Reactors() {
    }

    /**
     * 包装阻塞调用为 Mono，订阅时切换到 boundedElastic 线程执行。
     */
    public static <T> Mono<T> call(Callable<T> blocking) {
        return Mono.fromCallable(blocking).subscribeOn(Schedulers.boundedElastic());
    }
}
