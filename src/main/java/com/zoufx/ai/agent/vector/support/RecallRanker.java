package com.zoufx.ai.agent.vector.support;

import com.zoufx.ai.agent.vector.property.RecallProps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 召回打分原语（无状态，只读 {@link RecallProps} 配置）：时近性衰减、三维加权、余弦相似度。
 * MMR 选择循环在 {@code RecallServiceImpl} 里用这些原语实现。
 */
@Component
@RequiredArgsConstructor
public class RecallRanker {

    private final RecallProps props;

    /** 时近性 exp(-age/τ)，age 为毫秒；τ 取配置天数。 */
    public double recency(long ageMillis) {
        double tauMillis = (double) props.getRecencyTauDays() * 24L * 3600_000L;
        return Math.exp(-Math.max(0L, ageMillis) / tauMillis);
    }

    /** score = α·recency + β·importance + γ·relevance。 */
    public double finalScore(double relevance, double recency, double importance) {
        RecallProps.Weights w = props.getWeights();
        return w.getAlpha() * recency + w.getBeta() * importance + w.getGamma() * relevance;
    }

    /** 余弦相似度；任一向量为 null / 维度不符 / 零向量返回 0。 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
