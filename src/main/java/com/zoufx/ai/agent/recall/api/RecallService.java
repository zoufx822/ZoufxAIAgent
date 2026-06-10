package com.zoufx.ai.agent.recall.api;

import com.zoufx.ai.agent.recall.model.RecallResult;
import dev.langchain4j.data.embedding.Embedding;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 语义召回引擎：embed query → Qdrant 按 userId 过滤召回 → 三维加权 → MMR → Top-N → 回查正文。
 *
 * <p>自动召回与手动 {@code search_cold_memory} 工具共用本服务。失败吞为空列表，不阻断主流。
 */
public interface RecallService {

    /**
     * @param windowSince 滑窗最早一条 createdAt；非空时排除窗口内已可见的 cold 条目。auto-recall 传，手动工具传 null。
     */
    Mono<List<RecallResult>> recall(String userId, String query, int limit, @Nullable Long windowSince);

    /** query 已 embed 时的重载——auto-recall 与 cold-user 入库复用同一向量。 */
    Mono<List<RecallResult>> recallWith(String userId, Embedding embedding, int limit, @Nullable Long windowSince);
}
