package com.zoufx.ai.agent.memory.impl;

import com.zoufx.ai.agent.memory.api.MemoryStore;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 实现的 ChatMemoryStore + MemoryStoreContract（双接口共用一套数据通路）。
 *
 * 设计要点：
 * - 单文件 SQLite + WAL 模式（WAL PRAGMA 由 HikariCP connectionInitSql 注入，见 MemoryDataSourceConfig）
 * - LC4J {@link ChatMemoryStore} 接口在框架线程同步调用：直接走私有 *Blocking 方法
 * - 业务 {@link MemoryStore} 接口反应式签名：用 {@code Mono.fromCallable(...).subscribeOn(boundedElastic())} 包装相同的 *Blocking 方法
 * - {@code updateMessages} 语义为"全量替换"——LC4J 每次都传完整 list，所以事务里 DELETE + 批量 INSERT
 * - 序列化用 LC4J 自带的 {@link ChatMessageSerializer} / {@link ChatMessageDeserializer}，覆盖 ChatMessage 的所有子类型
 */
@Slf4j
@Component
public class SqliteChatMemoryStore implements ChatMemoryStore, MemoryStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SqliteChatMemoryStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc,
                                 @Qualifier("memoryTxTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     TEXT    NOT NULL,
                    role        TEXT    NOT NULL,
                    content     TEXT    NOT NULL,
                    created_at  INTEGER NOT NULL
                )
                """);
        // v0 阶段每个 userId 数据量小，单列索引足够；ORDER BY id 走 PK，create_at 暂不需要联合索引
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_user ON chat_messages(user_id)");
        log.info("SqliteChatMemoryStore schema ready (chat_messages)");
    }

    // ====== LC4J ChatMemoryStore（同步，框架线程调用）======

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return loadByUserIdBlocking(memoryId.toString());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        saveByUserIdBlocking(memoryId.toString(), messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        deleteByUserIdBlocking(memoryId.toString());
    }

    // ====== 业务 MemoryStore（反应式，自动 boundedElastic 调度）======

    @Override
    public Mono<List<ChatMessage>> loadByUserId(String userId) {
        return Mono.fromCallable(() -> loadByUserIdBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> saveByUserId(String userId, List<ChatMessage> messages) {
        return Mono.<Void>fromRunnable(() -> saveByUserIdBlocking(userId, messages))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteByUserId(String userId) {
        return Mono.<Void>fromRunnable(() -> deleteByUserIdBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 注意：本方法保持同步——见 {@link MemoryStore#isEmpty(String)} 文档解释为何不能反应式。
     */
    @Override
    public boolean isEmpty(String userId) {
        return isEmptyBlocking(userId);
    }

    // ====== 私有同步实现（被两套接口共享）======

    private List<ChatMessage> loadByUserIdBlocking(String userId) {
        return jdbc.query(
                "SELECT content FROM chat_messages WHERE user_id = ? ORDER BY id ASC",
                (rs, i) -> ChatMessageDeserializer.messageFromJson(rs.getString("content")),
                userId);
    }

    private void saveByUserIdBlocking(String userId, List<ChatMessage> messages) {
        // 同批写入按毫秒+偏移单调递增，避免 v1 引入 Memory Stream 时所有 created_at 撞车
        long base = System.currentTimeMillis();
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM chat_messages WHERE user_id = ?", userId);
            if (messages.isEmpty()) return;
            jdbc.batchUpdate(
                    "INSERT INTO chat_messages (user_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ChatMessage msg = messages.get(i);
                            ps.setString(1, userId);
                            ps.setString(2, msg.type().name());
                            ps.setString(3, ChatMessageSerializer.messageToJson(msg));
                            ps.setLong(4, base + i);
                        }

                        @Override
                        public int getBatchSize() {
                            return messages.size();
                        }
                    });
        });
    }

    private void deleteByUserIdBlocking(String userId) {
        jdbc.update("DELETE FROM chat_messages WHERE user_id = ?", userId);
    }

    private boolean isEmptyBlocking(String userId) {
        Integer exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM chat_messages WHERE user_id = ?)",
                Integer.class, userId);
        return exists == null || exists == 0;
    }
}
