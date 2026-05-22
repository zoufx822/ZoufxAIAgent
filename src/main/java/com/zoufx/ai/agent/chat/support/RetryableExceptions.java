package com.zoufx.ai.agent.chat.support;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import reactor.core.Exceptions;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * 判定一个异常是否值得重试——Reactor {@code retryWhen} 的 predicate 与工具内部的重试循环共用。
 *
 * <p>规则优先级（自上而下）：
 * <ol>
 *   <li>LC4J 显式标记的 {@code NonRetriableException} → false</li>
 *   <li>LC4J 显式标记的 {@code RetriableException}    → true</li>
 *   <li>{@code HttpException}：408 / 429 / 5xx 视为可重试</li>
 *   <li>网络层兜底：SocketTimeout / ConnectException / 通用 IOException → true</li>
 * </ol>
 */
public final class RetryableExceptions {

    private RetryableExceptions() {
    }

    public static boolean isRetryable(Throwable t) {
        Throwable e = Exceptions.unwrap(t);
        if (e instanceof NonRetriableException) return false;
        if (e instanceof RetriableException) return true;
        if (e instanceof HttpException he) {
            int s = he.statusCode();
            return s == 408 || s == 429 || s >= 500;
        }
        return e instanceof SocketTimeoutException
                || e instanceof ConnectException
                || e instanceof IOException;
    }
}
