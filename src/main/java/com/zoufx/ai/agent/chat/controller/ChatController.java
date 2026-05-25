package com.zoufx.ai.agent.chat.controller;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.chat.model.ChatEventMapper;
import com.zoufx.ai.agent.chat.model.ChatRequest;
import com.zoufx.ai.agent.chat.service.ChatService;
import com.zoufx.ai.agent.memory.api.ColdMemoryStore;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.model.ColdMemoryEntry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * AI Agent HTTP 入口（全部归 {@code /ai/*} 命名空间）。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /ai/chat}：SSE 流式聊天</li>
 *   <li>{@code GET  /ai/capabilities}：LLM 能力声明</li>
 *   <li>{@code GET  /ai/memory/hot/{userId}?type=...}：Hot Memory snapshot</li>
 *   <li>{@code GET  /ai/memory/cold/{userId}?limit=N}：最近 N 条 Cold Memory</li>
 * </ul>
 *
 * <p>无鉴权（开发环境）；真上线前必须补。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class ChatController {

    /** Cold Memory 单次拉取的硬上限，超过即裁剪到此值。 */
    private static final int COLD_MEMORY_LIMIT_MAX = 50;

    private final ChatService chatService;
    private final LlmCapabilities capabilities;
    private final HotMemoryStore hotMemoryStore;
    private final ColdMemoryStore coldMemoryStore;

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

    @GetMapping("/capabilities")
    public LlmCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Hot Memory snapshot：返回该 userId 在指定 type 下写入过的全部 key/value。
     *
     * <p>{@code type} 为必填 query param——强制调用方显式选择类型，避免未来加新 type
     * 时默认值带来的语义漂移。当前合法 type 见 {@link HotMemoryType}（v0.13 仅 user-impression）。
     */
    @GetMapping("/memory/hot/{userId}")
    public Mono<Map<String, String>> hotMemory(@PathVariable String userId,
                                               @RequestParam String type) {
        return Mono.fromCallable(() -> hotMemoryStore.snapshot(userId, type));
    }

    /** 最近 N 条 Cold Memory 经历流。按 created_at DESC 返回，limit 默认 5、上限 50。 */
    @GetMapping("/memory/cold/{userId}")
    public Mono<List<ColdMemoryEntry>> coldMemory(@PathVariable String userId,
                                                  @RequestParam(defaultValue = "5") int limit) {
        int effective = Math.max(1, Math.min(limit, COLD_MEMORY_LIMIT_MAX));
        return coldMemoryStore.loadRecent(userId, effective);
    }
}
