package com.zoufx.ai.agent.memory.model;

/**
 * 关系状态——慢变量，记录"我和这个用户走到哪一步"，不直接展示给用户。
 *
 * @param familiarity 熟悉度 0–1：随对话次数/深度缓慢增长
 * @param trust       信任 0–1：用户披露脆弱且回应得体时增长
 * @param warmth      近期基调 -1–1：正负情绪的滑动平均，随时间衰减
 * @param updatedAt   更新时间
 */
public record RelationState(double familiarity, double trust, double warmth, long updatedAt) {

    public static final RelationState EMPTY = new RelationState(0, 0, 0, 0);
}
