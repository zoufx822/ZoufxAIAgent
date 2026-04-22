package com.zoufx.ai.agent.controller;

import com.zoufx.ai.agent.model.ChatRequest;
import com.zoufx.ai.agent.service.AIChatService;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : "default";
        String prompt = StringUtils.hasText(request.getPrompt()) ? request.getPrompt().trim() : "";
        log.info("Received prompt [sessionId={}, thinking={}]: {}", sessionId, request.isThinking(), prompt);

        return chatService.chat(sessionId, prompt, request.isThinking())
                .map(e -> ServerSentEvent.<String>builder().event(e.type()).data(e.data()).build());
    }

    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return Map.of("cleared", sessionId);
    }
}
