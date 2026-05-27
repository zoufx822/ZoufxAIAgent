package com.zoufx.ai.agent.chat.controller;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.chat.model.ChatEventMapper;
import com.zoufx.ai.agent.chat.model.AnchorCreateRequest;
import com.zoufx.ai.agent.chat.model.AnchorTitleUpdateRequest;
import com.zoufx.ai.agent.chat.model.ChatRequest;
import com.zoufx.ai.agent.chat.service.ChatService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import com.zoufx.ai.agent.memory.api.ChatMemoryStore;
import com.zoufx.ai.agent.memory.api.ColdMemoryStore;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.model.AnchorMemoryEntry;
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
 *   <li>{@code POST   /ai/chat}：SSE 流式聊天（按 anchorId 隔离）</li>
 *   <li>{@code GET    /ai/capabilities}：LLM 能力声明</li>
 *   <li>{@code POST   /ai/anchor}：创建新对话锚点</li>
 *   <li>{@code GET    /ai/anchors/{userId}}：列出该用户全部锚点（sidebar）</li>
 *   <li>{@code PATCH  /ai/anchor/{anchorId}/title}：手动重命名锚点</li>
 *   <li>{@code GET    /ai/anchor/{anchorId}/messages}：加载锚点消息历史（≤20 条 window）</li>
 *   <li>{@code GET    /ai/memory/hot/{userId}?type=...}：Hot Memory snapshot（用户级）</li>
 *   <li>{@code GET    /ai/memory/cold/{userId}?limit=N}：最近 N 条 Cold Memory（用户级）</li>
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
    private final AnchorMemoryStore anchorMemoryStore;
    private final ChatMemoryStore chatMemoryStore;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request, ServerHttpResponse response) {
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set("Cache-Control", "no-cache");

        String anchorId = request.anchorId();
        String prompt = request.prompt().trim();
        log.info("Received prompt [anchorId={}, prevAnchorId={}, thinking={}]: {}",
                anchorId, request.prevAnchorId(), request.thinking(), prompt);

        return chatService.chat(anchorId, prompt, request.thinking(), request.prevAnchorId())
                .map(ChatEventMapper::toSse);
    }

    @GetMapping("/capabilities")
    public LlmCapabilities capabilities() {
        return capabilities;
    }

    // ====== Anchor lifecycle ======

    /** 列出该用户全部锚点，按 last_active_at desc。 */
    @GetMapping("/anchors/{userId}")
    public Mono<List<AnchorMemoryEntry>> listAnchors(@PathVariable String userId) {
        return anchorMemoryStore.listByUser(userId);
    }

    /** 加载锚点消息历史（≤20 条 window），供前端切锚点时显示。 */
    @GetMapping("/anchor/{anchorId}/messages")
    public Mono<List<Map<String, String>>> messages(@PathVariable String anchorId) {
        return chatMemoryStore.loadByAnchorId(anchorId)
                .map(list -> list.stream().map(ChatController::toMessageView).toList());
    }

    /** 创建新对话锚点。title 可空，首条 chat 完成后由 ChatService 用首条 user 消息自动 backfill。 */
    @PostMapping("/anchor")
    public Mono<AnchorMemoryEntry> createAnchor(@Valid @RequestBody AnchorCreateRequest request) {
        return anchorMemoryStore.create(request.userId(), request.title());
    }

    /** 手动重命名锚点——无条件覆盖。 */
    @PatchMapping("/anchor/{anchorId}/title")
    public Mono<Void> renameAnchor(@PathVariable String anchorId,
            @Valid @RequestBody AnchorTitleUpdateRequest request) {
        return anchorMemoryStore.updateTitle(anchorId, request.title().trim());
    }

    private static Map<String, String> toMessageView(ChatMessage msg) {
        String role = msg.type().name().toLowerCase();
        String content = "";
        if (msg instanceof UserMessage u) {
            content = u.singleText();
        } else if (msg instanceof AiMessage a) {
            String t = a.text();
            if (t != null) content = t;
        }
        return Map.of("role", role, "content", content);
    }

    // ====== Memory snapshots ======

    /**
     * Hot Memory snapshot：返回该 userId 在指定 type 下写入过的全部 key/value。
     *
     * <p>{@code type} 为必填 query param——强制调用方显式选择类型，避免未来加新 type
     * 时默认值带来的语义漂移。当前合法 type 见 {@link HotMemoryType}。
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
