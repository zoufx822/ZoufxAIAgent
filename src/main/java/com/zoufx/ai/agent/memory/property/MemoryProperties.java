package com.zoufx.ai.agent.memory.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆系统统一配置（prefix=ai.memory）。
 *
 * <p>覆盖会话记忆、持久化存储、Hot Memory 各 type 参数。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class MemoryProperties {

    /** ChatMemory 窗口最大消息数 */
    private int maxMessages = 20;

    private Store store = new Store();
    private Hot hot = new Hot();

    @Data
    public static class Store {
        /** SQLite 数据库文件路径 */
        private String dbPath = "./data/zoufx-ai.db";
    }

    @Data
    public static class Hot {
        private UserImpression userImpression = new UserImpression();
        private SignificantEvent significantEvent = new SignificantEvent();
        private Commitment commitment = new Commitment();
    }

    @Data
    public static class UserImpression {
        private Completeness completeness = new Completeness();
    }

    @Data
    public static class Completeness {
        /** fill_ratio < 此值进入 stranger mode */
        private double strangerThreshold = 0.3;
        /** fill_ratio ≥ 此值进入 fully-known mode */
        private double fullyKnownThreshold = 0.7;
    }

    @Data
    public static class SignificantEvent {
        /** 注入 prompt 的最近条数 */
        private int recentInjectLimit = 5;
    }

    @Data
    public static class Commitment {
        /** 注入 prompt 的最近条数 */
        private int recentInjectLimit = 5;
    }
}
