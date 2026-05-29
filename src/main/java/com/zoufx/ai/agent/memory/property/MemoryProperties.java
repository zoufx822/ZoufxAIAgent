package com.zoufx.ai.agent.memory.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆系统统一配置（prefix=ai.memory）。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class MemoryProperties {

    /** 对话窗口最大消息数 */
    private int maxMessages = 20;

    private Store store = new Store();

    @Data
    public static class Store {
        /** SQLite 数据库文件路径 */
        private String dbPath = "./data/zoufx-ai.db";
    }
}
