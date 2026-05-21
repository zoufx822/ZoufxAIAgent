package com.zoufx.ai.agent.memory.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话记忆配置（v0.13 从 {@code chat/property/ChatMemoryProperties} 改名挪包）。
 *
 * <p>外部化原 ChatMemoryConfig 的硬编码常量 MAX_MESSAGES。
 *
 * <p>v0.13 命名空间重整：与 yml 节 {@code ai.memory} 对应，按"Properties 类归对应业务模块"
 * 原则从 chat 包挪到 memory 包；类名去掉 Chat 前缀（前缀冗余——它本就归 memory 模块）。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class MemoryProperties {

    private int maxMessages = 20;
}
