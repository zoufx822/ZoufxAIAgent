package com.zoufx.ai.agent.memory.api;

import com.zoufx.ai.agent.memory.model.AnchorMemoryEntry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 锚点记忆业务接口——管理 anchor_memory 表，承载 anchorId ↔ userId 关联 + title / summary 缓存。
 *
 * <p><b>读/写签名分裂原因</b>：
 * <ul>
 *   <li>{@link #findUserId} / {@link #listOtherAnchorsSync} / {@link #snapshotActiveAt} 同步——
 *       调用方 PromptSection.render / @Tool 方法不能 .block() 也不便包 Mono</li>
 *   <li>create / touch / updateSummaryIfUnchanged / updateTitleIfBlank 反应式——
 *       调用方在 WebFlux Controller / ChatService 流式编排上，需要反应式串接</li>
 * </ul>
 */
public interface AnchorMemoryStore {

    /**
     * 同步反查 userId——给 PromptSection 与 @Tool 方法使用。
     * anchorId 不存在返回 null，调用方自行处理（一般跳过本段 / 返回工具调用失败）。
     */
    @Nullable String findUserId(String anchorId);

    /**
     * 同步加载该用户的其他锚点（排除当前 anchorId），按 last_active_at desc。
     * 供 {@code AnchorContextSection.render} 用。
     */
    List<AnchorMemoryEntry> listOtherAnchorsSync(String userId, String excludeAnchorId);

    /**
     * 同步读取锚点当前 last_active_at——供 {@link com.zoufx.ai.agent.chat.service.AnchorService#compress}
     * 在压缩前快照，写回时做 CAS 防止覆盖 touch 后的活跃状态。
     * anchorId 不存在返回 null。
     */
    @Nullable Long snapshotActiveAt(String anchorId);

    /**
     * 同步创建锚点，anchorId 由调用方（前端）提供。
     * 供 ChatService 在首次 chat 时懒创建，与 {@link #findUserId} 对称，均在 boundedElastic 线程调用。
     */
    void createSync(String anchorId, String userId);

    /**
     * 列出该用户全部锚点，按 last_active_at desc。给前端 sidebar 用。
     */
    Mono<List<AnchorMemoryEntry>> listByUser(String userId);

    /**
     * 标记锚点为活跃——更新 last_active_at = now，同时把 summary 置 NULL（回访场景，旧摘要作废），
     * 并把本轮 AI 最后一次 mood 写入 last_mood（COALESCE 语义：null 不覆盖旧值，保留"上次的情绪"）。
     * 由 ChatService.onStreamComplete 调用。
     */
    Mono<Void> touch(String anchorId, @Nullable String lastMood);

    /**
     * CAS 写入压缩摘要——仅当 last_active_at 与快照值一致时才写入。
     * 若 touch 在压缩期间推进了时间戳，CAS 不匹配 → 静默丢弃过时摘要，summary 维持 NULL（活跃状态）。
     * 由 {@link com.zoufx.ai.agent.chat.service.AnchorService#compress} 调用。
     */
    Mono<Void> updateSummaryIfUnchanged(String anchorId, String summary, long snapshotAt);

    /**
     * 仅当 title 为 null / 空白时填入——避免覆盖用户手动改过的标题。
     * 由 ChatService.onStreamComplete 用首条用户消息截取后调用。
     */
    Mono<Void> updateTitleIfBlank(String anchorId, String title);

    /**
     * 无条件覆盖 title——由前端 PATCH /ai/anchors/{anchorId}/title 用户手动改名时调。
     * 与 {@link #updateTitleIfBlank} 区分：后者只填空，本方法强写。
     */
    Mono<Void> updateTitle(String anchorId, String title);

    /**
     * 删除锚点元数据。注意：本方法只删 anchor_memory 表，消息流要单独调
     * {@link ChatMemoryStore#deleteByAnchorId} 才清干净。
     */
    Mono<Void> delete(String anchorId);
}
