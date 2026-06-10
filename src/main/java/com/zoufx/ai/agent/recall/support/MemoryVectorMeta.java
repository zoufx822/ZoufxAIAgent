package com.zoufx.ai.agent.recall.support;

/**
 * 向量 point 的 payload 元数据 key 常量 + memType 取值常量。
 *
 * <p>Qdrant 只存向量 + 这些指针元数据，==不存正文==；召回时凭 memType + sourceId 回 SQLite 取原文。
 * memType 取值与 {@code HotMemoryType} 对齐（hot 三类）+ cold。
 */
public final class MemoryVectorMeta {

    // ===== payload 元数据 key =====
    public static final String USER_ID = "userId";
    public static final String MEM_TYPE = "memType";
    public static final String SOURCE_ID = "sourceId";
    public static final String ROLE = "role";
    public static final String CREATED_AT = "createdAt";
    public static final String IMPORTANCE = "importance";

    // ===== memType 取值 =====
    public static final String COLD = "cold";
    public static final String SIGNIFICANT_EVENT = "significant-event";
    public static final String COMMITMENT = "commitment";
    public static final String USER_IMPRESSION = "user-impression";

    /** memType 的中文短标签（召回结果展示用）。 */
    public static String labelOf(String memType) {
        return switch (memType) {
            case SIGNIFICANT_EVENT -> "经历";
            case COMMITMENT -> "承诺";
            case USER_IMPRESSION -> "画像";
            default -> "对话";
        };
    }

    private MemoryVectorMeta() {}
}
