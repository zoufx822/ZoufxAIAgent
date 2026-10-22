package com.zoufx.ai.agent.memory.api;

import com.zoufx.ai.agent.memory.model.RelationState;

/**
 * 关系状态的存储接口——每用户一行，随轮次累计。
 * 同步签名：调用方均在 boundedElastic 或事务线程上。
 */
public interface RelationStateDao {

    /** 读取关系状态；无记录返回 {@link RelationState#EMPTY}。 */
    RelationState load(String userId);

    /** 覆盖写入关系状态。 */
    void save(String userId, RelationState state);
}
