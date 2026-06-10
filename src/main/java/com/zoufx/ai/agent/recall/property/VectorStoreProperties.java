package com.zoufx.ai.agent.recall.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant 向量库配置。
 *
 * <p>Qdrant 是记忆原文的==派生索引==——只存向量 + 指针元数据，不存正文（正文留在 SQLite 正本）。
 * 本地走 docker（gRPC 6334）；用 Qdrant Cloud 时设 host + apiKey + useTls。
 */
@Data
@ConfigurationProperties(prefix = "ai.vector")
public class VectorStoreProperties {

    private String host = "localhost";
    /** gRPC 端口（LC4J QdrantEmbeddingStore 走 gRPC）。 */
    private int port = 6334;
    private String collection = "memory_vectors";
    /** 必须与 {@code ai.embedding.dimension} 一致；与既有 collection 不一致时拒绝启动。 */
    private int dimension;
    /** cosine | dot | euclid | manhattan。 */
    private String distance = "cosine";
    private boolean useTls = false;
    /** 可空：Qdrant Cloud 鉴权用；本地 docker 留空。 */
    private String apiKey;
}
