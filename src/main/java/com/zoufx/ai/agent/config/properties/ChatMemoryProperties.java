package com.zoufx.ai.agent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话记忆配置。
 * 外部化原 ChatMemoryConfig 的硬编码常量 MAX_MESSAGES。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class ChatMemoryProperties {

    private int maxMessages = 20;
}
