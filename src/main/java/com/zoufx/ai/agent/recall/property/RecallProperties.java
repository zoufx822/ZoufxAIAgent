package com.zoufx.ai.agent.recall.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 召回引擎配置：三维加权 / 时近性衰减 / 召回条数 / MMR。
 */
@Data
@ConfigurationProperties(prefix = "ai.recall")
public class RecallProperties {

    private Weights weights = new Weights();
    /** 时近性 exp(-Δt/τ) 的 τ（天）。 */
    private int recencyTauDays = 7;
    /** Qdrant 一阶召回数。 */
    private int topK = 30;
    /** cosine 下限，过滤无关项。 */
    private double minScore = 0.3;
    /** 注入 prompt 的最终条数。 */
    private int limit = 5;
    private Mmr mmr = new Mmr();
    /** 启动时 backfill 存量记忆到向量库（默认关）。 */
    private boolean backfillOnStart = false;

    /** 三维加权：recency / importance / relevance。 */
    @Data
    public static class Weights {
        private double alpha = 1.0;
        private double beta = 1.0;
        private double gamma = 1.5;
    }

    @Data
    public static class Mmr {
        private boolean enabled = true;
        /** 1.0 = 完全相关性优先；0 = 完全多样性优先。 */
        private double lambda = 0.7;
    }
}
