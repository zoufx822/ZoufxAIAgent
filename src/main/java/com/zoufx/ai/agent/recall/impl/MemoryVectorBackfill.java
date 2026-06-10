package com.zoufx.ai.agent.recall.impl;

import com.zoufx.ai.agent.memory.support.UserImpressionFields;
import com.zoufx.ai.agent.recall.api.MemoryIndexer;
import com.zoufx.ai.agent.recall.support.MemoryVectorMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 存量记忆回填到向量库——仅 {@code ai.recall.backfill-on-start=true} 时启用（默认关）。
 *
 * <p>遍历 cold_memory + hot_memory 全量，逐条 embed + 写入 Qdrant（确定性 id，重跑幂等不重复）。
 * 每条 {@code .block()} 串行执行以自然限流；这是一次性手动操作，慢可接受。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.recall.backfill-on-start", havingValue = "true")
public class MemoryVectorBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final MemoryIndexer indexer;

    public MemoryVectorBackfill(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc, MemoryIndexer indexer) {
        this.jdbc = jdbc;
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("MemoryVectorBackfill START (this may take a while)");
        AtomicInteger cold = new AtomicInteger();
        jdbc.query("SELECT id, user_id, role, content, created_at FROM cold_memory ORDER BY id", rs -> {
            String userId = rs.getString("user_id");
            String role = rs.getString("role");
            String content = rs.getString("content");
            long createdAt = rs.getLong("created_at");
            indexer.index(userId, MemoryVectorMeta.COLD, String.valueOf(rs.getLong("id")),
                    content, role, createdAt).block();
            cold.incrementAndGet();
        });

        AtomicInteger hot = new AtomicInteger();
        jdbc.query("SELECT user_id, type, key, value, updated_at FROM hot_memory", rs -> {
            String userId = rs.getString("user_id");
            String type = rs.getString("type");
            String key = rs.getString("key");
            String value = rs.getString("value");
            long updatedAt = rs.getLong("updated_at");
            String embedText = embedTextFor(type, key, value);
            indexer.index(userId, type, key, embedText, null, updatedAt).block();
            hot.incrementAndGet();
        });

        log.info("MemoryVectorBackfill DONE cold={} hot={}", cold.get(), hot.get());
    }

    /** user-impression 嵌入带字段语义的短句（与 UserImpressionUpdateTool 共用 helper）；其余直接 embed value。 */
    private String embedTextFor(String type, String key, String value) {
        return MemoryVectorMeta.USER_IMPRESSION.equals(type)
                ? UserImpressionFields.embedText(key, value)
                : value;
    }
}
