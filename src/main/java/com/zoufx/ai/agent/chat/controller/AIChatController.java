package com.zoufx.ai.agent.chat.controller;

import com.zoufx.ai.agent.chat.model.ChatEventMapper;
import com.zoufx.ai.agent.chat.model.ChatRequest;
import com.zoufx.ai.agent.chat.service.AIChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI 控制器 —— HTTP 适配层。
 * 职责：HTTP 头设置 + 委托 service + 领域事件到 SSE 协议的翻译。
 *
 * 入参校验由 {@link jakarta.validation.Valid @Valid} + {@link com.zoufx.ai.agent.model.ChatRequest} 上的 Bean Validation 注解承担；
 * 校验失败由 {@link GlobalExceptionHandler} 统一翻译为 HTTP 400，不再走 SSE error 事件分支。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AIChatController {

    private final AIChatService chatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request, ServerHttpResponse response) {
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set("Cache-Control", "no-cache");

        String userId = request.getUserId();
        String prompt = request.getPrompt().trim();
        log.info("Received prompt [userId={}, thinking={}]: {}", userId, request.isThinking(), prompt);

        return chatService.chat(userId, prompt, request.isThinking())
                .map(ChatEventMapper::toSse);
    }

    @DeleteMapping("/user/{userId}/memory")
    public Mono<Map<String, Object>> clearUserMemory(@PathVariable String userId) {
        return chatService.clearUserMemory(userId)
                .thenReturn(Map.of("cleared", userId));
    }
}
