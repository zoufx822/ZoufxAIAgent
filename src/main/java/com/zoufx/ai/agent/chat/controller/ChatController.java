package com.zoufx.ai.agent.chat.controller;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.chat.model.AnchorContextView;
import com.zoufx.ai.agent.chat.model.AnchorCreateRequest;
import com.zoufx.ai.agent.chat.model.AnchorTitleUpdateRequest;
import com.zoufx.ai.agent.chat.model.ChatRequest;
import com.zoufx.ai.agent.chat.service.ChatService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import com.zoufx.ai.agent.memory.api.ChatMemoryStore;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.model.AnchorMemoryEntry;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * AI Agent HTTP 入口（全部归 {@code /ai/*} 命名空间）。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST   /ai/chat}：SSE 流式聊天（按 anchorId 隔离）</li>
 *   <li>{@code GET    /ai/capabilities}：LLM 能力声明</li>
 *   <li>{@code POST   /ai/anchors}：创建新对话锚点</li>
 *   <li>{@code GET    /ai/anchors?userId=X}：列出该用户全部锚点（sidebar）</li>
 *   <li>{@code GET    /ai/anchors/{anchorId}/messages}：加载锚点消息历史（≤20 条 window）</li>
 *   <li>{@code GET    /ai/anchors/{anchorId}/context}：其他锚点三层衰减视图（near/mid/far）</li>
 *   <li>{@code PATCH  /ai/anchors/{anchorId}/title}：手动重命名锚点</li>
 *   <li>{@code GET    /ai/memory/hot?userId=X&type=Y}：Hot Memory snapshot（用户级）</li>
 * </ul>
 *
 * <p>URL 风格约定：资源集合一律复数；userId 作为过滤条件走 query param，
 * 资源主键（anchorId）走 path param。无鉴权（开发环境）；真上线前必须补。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class ChatController {

    private final ChatService chatService;
    private final LlmCapabilities capabilities;
    private final HotMemoryStore hotMemoryStore;
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
                .map(e -> ServerSentEvent.<String>builder().event(e.type()).data(e.data()).build());
    }

    @GetMapping("/capabilities")
    public LlmCapabilities capabilities() {
        return capabilities;
    }

    // ====== Anchor lifecycle ======

    /** 列出该用户全部锚点，按 last_active_at desc。 */
    @GetMapping("/anchors")
    public Mono<List<AnchorMemoryEntry>> listAnchors(@RequestParam String userId) {
        return anchorMemoryStore.listByUser(userId);
    }

    /** 加载锚点消息历史（≤20 条 window），供前端切锚点时显示。 */
    @GetMapping("/anchors/{anchorId}/messages")
    public Mono<List<Map<String, String>>> messages(@PathVariable String anchorId) {
        return chatMemoryStore.loadByAnchorId(anchorId)
                .map(list -> list.stream().map(ChatController::toMessageView).toList());
    }

    /**
     * 当前锚点的"其他锚点"三层衰减视图（near/mid/far），供前端右栏「记忆锚点」section 渲染。
     * anchorId 不存在时返 200 空结构，让前端统一走"这是我们的第一次对话"空态。
     */
    @GetMapping("/anchors/{anchorId}/context")
    public Mono<AnchorContextView> anchorContext(@PathVariable String anchorId) {
        return Mono.fromCallable(() -> {
            String userId = anchorMemoryStore.findUserId(anchorId);
            if (userId == null) return AnchorContextView.empty();
            return AnchorContextView.from(anchorMemoryStore.listOtherAnchorsSync(userId, anchorId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 创建新对话锚点。title 可空，首条 chat 完成后由 ChatService 用首条 user 消息自动 backfill。 */
    @PostMapping("/anchors")
    public Mono<AnchorMemoryEntry> createAnchor(@Valid @RequestBody AnchorCreateRequest request) {
        return anchorMemoryStore.create(request.userId(), request.title());
    }

    /** 手动重命名锚点——无条件覆盖。 */
    @PatchMapping("/anchors/{anchorId}/title")
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
    @GetMapping("/memory/hot")
    public Mono<Map<String, String>> hotMemory(@RequestParam String userId,
                                               @RequestParam String type) {
        return Mono.fromCallable(() -> hotMemoryStore.snapshot(userId, type));
    }

}
