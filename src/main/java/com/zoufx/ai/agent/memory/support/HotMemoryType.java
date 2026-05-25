package com.zoufx.ai.agent.memory.support;

/**
 * hot_memory.type 字段的字符串常量。
 *
 * <p>用字符串常量而非枚举——type 在 DB 落字面、yml 配字面、prompt 写字面，
 * 全链路字符串语义一致，不用类型转换。
 */
public final class HotMemoryType {

    public static final String USER_IMPRESSION = "user-impression";
    public static final String SIGNIFICANT_EVENT = "significant-event";
    public static final String COMMITMENT = "commitment";

    private HotMemoryType() {}
}
