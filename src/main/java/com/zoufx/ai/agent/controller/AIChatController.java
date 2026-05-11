package com.zoufx.ai.agent.controller;

import com.zoufx.ai.agent.model.ChatRequest;
import com.zoufx.ai.agent.service.AIChatService;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI 控制器 —— HTTP 适配层。
 * 负责：请求解析、响应头设置、ChatEvent → SSE 事件翻译。
 * 业务逻辑见 {@link AIChatService}；网络检索工具方法见 {@link WebSearchEventHelper}。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AIChatController {

    @Autowired
    private AIChatService chatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request, ServerHttpResponse response) {
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set("Cache-Control", "no-cache");

        if (!StringUtils.hasText(request.getUserId())) {
            log.warn("Rejected request: missing userId");
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("userId 不能为空")
                    .build());
        }

        String userId = request.getUserId();
        String prompt = StringUtils.hasText(request.getPrompt()) ? request.getPrompt().trim() : "";
        log.info("Received prompt [userId={}, thinking={}]: {}", userId, request.isThinking(), prompt);

        return chatService.chat(userId, prompt, request.isThinking())
                .map(e -> ServerSentEvent.<String>builder().event(e.type()).data(e.data()).build());
    }

    @DeleteMapping("/user/{userId}/memory")
    public Mono<Map<String, Object>> clearUserMemory(@PathVariable String userId) {
        return chatService.clearUserMemory(userId)
                .thenReturn(Map.of("cleared", userId));
    }
}
