package com.zoufx.ai.agent.chat.model;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 领域事件 {@link ChatEvent} → HTTP SSE 协议事件的翻译器。
 *
 * Controller 边界专用：把"事件类型/载荷"的领域语义翻译成 SSE wire format。
 * 当未来 SSE 协议升级或多通道接入（IM/WebSocket）时，只需在这里追加映射器。
 */
public final class ChatEventMapper {

    private ChatEventMapper() {}

    public static ServerSentEvent<String> toSse(ChatEvent event) {
        return ServerSentEvent.<String>builder()
                .event(event.type())
                .data(event.data())
                .build();
    }
}
