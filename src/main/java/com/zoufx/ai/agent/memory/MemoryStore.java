package com.zoufx.ai.agent.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 记忆存储业务接口。
 *
 * LC4J 自带的 {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} 是技术接口，
 * key 类型是 {@code Object memoryId}；这里抽一层窄接口面向业务（{@code String userId}），
 * 方便后续扩展 recall / search / 分类型查询，且业务代码绑接口不绑实现。
 */
public interface MemoryStore {

    List<ChatMessage> loadByUserId(String userId);

    void saveByUserId(String userId, List<ChatMessage> messages);

    void deleteByUserId(String userId);

    /**
     * 用于"陌生人识别"——记忆为空 = AI 不认识此人。
     * 实现应使用 COUNT/EXISTS 等开销极小的查询。
     */
    boolean isEmpty(String userId);
}
