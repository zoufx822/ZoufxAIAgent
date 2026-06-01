package com.zoufx.ai.agent.memory.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆存储配置（prefix=ai.memory）。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class MemoryProperties {

    /** SQLite 数据库文件路径 */
    private String dbPath = "./data/zoufx-ai.db";
}
