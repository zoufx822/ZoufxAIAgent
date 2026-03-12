package com.zoufxdemo.zoufxlangchain4j.service.impl;

import com.zoufxdemo.zoufxlangchain4j.service.ChatMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版本的聊天记忆服务实现
 * 使用 ConcurrentHashMap 存储会话，支持多会话
 * 后续如需切换到 Redis，只需新建 ChatMemoryServiceImpl 实现 ChatMemoryService 接口
 */
@Slf4j
@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    /**
     * 使用 ConcurrentHashMap 存储会话消息
     * Key: sessionId
     * Value: 消息列表
     */
    private final ConcurrentHashMap<String, List<String>> sessionMessages = new ConcurrentHashMap<>();

    @Override
    public void addUserMessage(String sessionId, String message) {
        log.debug("Adding user message to session {}: {}", sessionId, message);
        addMessage(sessionId, "User: " + message);
    }

    @Override
    public void addAssistantMessage(String sessionId, String message) {
        log.debug("Adding assistant message to session {}: {}", sessionId, message);
        addMessage(sessionId, "Assistant: " + message);
    }

    @Override
    public List<String> getHistory(String sessionId) {
        List<String> messages = sessionMessages.getOrDefault(sessionId, new ArrayList<>());
        log.debug("Retrieved {} messages from session {}", messages.size(), sessionId);
        return new ArrayList<>(messages);
    }

    @Override
    public void clear(String sessionId) {
        log.info("Clearing session memory: {}", sessionId);
        sessionMessages.remove(sessionId);
    }

    /**
     * 内部方法：添加消息到指定会话
     */
    private void addMessage(String sessionId, String message) {
        sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }
}
