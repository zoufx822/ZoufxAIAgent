package com.zoufx.ai.agent.memory.api;

/**
 * hot_memory.type 字段的常量值（v0.13 引入）。
 *
 * <p>hot_memory 是父概念——见到此人就立刻浮现、不需检索的常驻记忆。
 * 内部按 type 区分子类：
 * <ul>
 *   <li>{@link #USER_IMPRESSION}：稳定的用户画像属性（结构化 KV）</li>
 *   <li>未来 significant-event：重要事件 / 承诺 / 状态（叙事性，append-only）</li>
 * </ul>
 *
 * <p>v0.13 仅落地 {@code user-impression}；schema 已为未来 type 留好骨架。
 *
 * <p>不用枚举：type 在 DB 落字符串、yml 配字符串、prompt 写字符串，
 * 全链路自然；用枚举反而处处要转换。
 */
public final class HotMemoryType {

    public static final String USER_IMPRESSION = "user-impression";

    // 未来扩展位：
    // public static final String SIGNIFICANT_EVENT = "significant-event";

    private HotMemoryType() {}
}
