package com.zoufx.ai.agent.chat.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.Map;

/**
 * LC4J ChatMessage → 前端视图转换工具。
 */
public final class ChatMessageMapper {

    private ChatMessageMapper() {}

    public static Map<String, String> toMessageView(ChatMessage msg) {
        String role = msg.type().name().toLowerCase();
        String content = "";
        if (msg instanceof UserMessage u) {
            content = u.singleText();
        } else if (msg instanceof AiMessage a) {
            String t = a.text();
            if (t != null) content = t;
        }
        return Map.of("role", role, "content", content);
    }
}
