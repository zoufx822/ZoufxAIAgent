package com.zoufx.ai.agent.chat.model;

/**
 * {@link com.zoufx.ai.agent.chat.service.ChatService#prepare} 的返回值：
 * 解析后的 anchorId + 是否本次新建。
 */
public record ChatPrepared(String anchorId, boolean newAnchor) {
}
