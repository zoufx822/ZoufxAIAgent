package com.zoufx.ai.agent.vector.api;

import com.zoufx.ai.agent.vector.model.RecallResult;
import dev.langchain4j.data.embedding.Embedding;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 语义召回引擎：Qdrant 按 userId 过滤召回 → 三维加权 → MMR → Top-N → 回查正文。
 *
 * <p>自动召回（ChatService.prepare，boundedElastic）与手动 {@code search_cold_memory} 工具（@Tool 线程）
 * 共用本服务。失败吞为空列表，不阻断主流。
 */
public interface RecallService {

    /**
     * 召回流水线：Qdrant 搜索 → 三维加权 → MMR → Top-N → hydrate 回查正文。
     * 同步签名，调用方在 boundedElastic 或 @Tool 线程上（均允许阻塞）。失败吞为空列表。
     */
    List<RecallResult> recall(String userId, Embedding embedding, int limit, @Nullable Long windowSince);
}
