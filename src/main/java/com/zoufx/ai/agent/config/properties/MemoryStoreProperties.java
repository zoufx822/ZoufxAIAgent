package com.zoufx.ai.agent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆持久化存储配置。v0 阶段对应 SQLite 单文件落盘。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory.store")
public class MemoryStoreProperties {

    /**
     * SQLite 数据库文件路径。默认 ./data/zoufx-ai.db，相对工作目录解析。
     */
    private String dbPath = "./data/zoufx-ai.db";
}
