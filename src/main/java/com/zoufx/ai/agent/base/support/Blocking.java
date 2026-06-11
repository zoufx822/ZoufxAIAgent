package com.zoufx.ai.agent.base.support;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 阻塞代码进入 Reactor 世界的唯一换线程入口，统一调度到 boundedElastic。
 *
 * <p>使用约束：只允许出现在两类位置——store 的 {@code xxxAsync} 包装方法、
 * service 自包含阻塞流水线（顺序阻塞 IO + 提前退出，无并发/流式需求）的最外层。
 * 编排链路中段不要再手写 {@code Mono.fromCallable + subscribeOn}：多步阻塞逻辑
 * 先写成纯同步私有方法，再在公开边界整体包一次。
 */
public final class Blocking {

    private Blocking() {
    }

    /** 有返回值的阻塞任务；task 返回 null 时 Mono 直接完成、不发射元素。 */
    public static <T> Mono<T> call(Callable<T> task) {
        return Mono.fromCallable(task).subscribeOn(Schedulers.boundedElastic());
    }

    /** 无返回值的阻塞任务。 */
    public static Mono<Void> run(Runnable task) {
        return Mono.<Void>fromRunnable(task).subscribeOn(Schedulers.boundedElastic());
    }
}
