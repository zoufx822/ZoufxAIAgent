package com.zoufx.ai.agent.util;

import com.zoufx.ai.agent.config.properties.RetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.util.retry.Retry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM 调用的重试策略，按业务语义命名而非按 Reactor API 描述。
 *
 * 抽出动机：
 * - 原本散在 {@code AIChatService.buildRetrySpec}，{@code hasEmitted} 是与 retry filter 强耦合的状态——
 *   它的"流是否已开始"语义其实是 retry 策略本身的一部分，不应散落在 service 里
 * - 抽出后可独立单测（mock {@code hasEmitted} 验证 retry 触发与否），AIChatService 调用一行
 *
 * 暴露的唯一方法 {@link #onlyRetryBeforeFirstEmission(AtomicBoolean)}：
 * 仅在"流尚未推送任何事件"时重试，避免给前端发出半截 token 后又重来。
 * 触发条件是 {@code !hasEmitted.get() && RetryPolicy.isRetryable(err)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRetrySpec {

    private final RetryProperties retryProperties;

    /**
     * 构造一个 Reactor {@link Retry}：仅在首次 emit 前对可重试错误生效，按指数退避重试。
     * 调用方需持有 {@code hasEmitted}，并在 LC4J 任一回调（thinking/content/tool_call/tool_result）
     * 触发时置为 true。
     */
    public Retry onlyRetryBeforeFirstEmission(AtomicBoolean hasEmitted) {
        RetryProperties.Llm cfg = retryProperties.getLlm();
        return Retry.backoff(cfg.getMaxAttempts(), cfg.getMinBackoff())
                .maxBackoff(cfg.getMaxBackoff())
                .filter(err -> !hasEmitted.get() && RetryPolicy.isRetryable(err))
                .doBeforeRetry(rs -> log.warn("LLM retry #{} cause={}",
                        rs.totalRetries() + 1, rs.failure().toString()));
    }
}
