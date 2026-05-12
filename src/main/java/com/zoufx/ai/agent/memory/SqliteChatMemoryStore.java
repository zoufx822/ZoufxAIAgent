package com.zoufx.ai.agent.memory;

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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 实现的 ChatMemoryStore + MemoryStore（双接口共用一套数据通路）。
 *
 * 设计要点：
 * - 单文件 SQLite + WAL 模式（WAL PRAGMA 由 HikariCP connectionInitSql 注入，见 MemoryDataSourceConfig）
 * - LC4J 调用 ChatMemoryStore 走框架自身调度器（与 @Tool 同情境），不影响 WebFlux event loop
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

    // ====== LC4J ChatMemoryStore ======

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return loadByUserId(memoryId.toString());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        saveByUserId(memoryId.toString(), messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        deleteByUserId(memoryId.toString());
    }

    // ====== 业务 MemoryStore ======

    @Override
    public List<ChatMessage> loadByUserId(String userId) {
        return jdbc.query(
                "SELECT content FROM chat_messages WHERE user_id = ? ORDER BY id ASC",
                (rs, i) -> ChatMessageDeserializer.messageFromJson(rs.getString("content")),
                userId);
    }

    @Override
    public void saveByUserId(String userId, List<ChatMessage> messages) {
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

    @Override
    public void deleteByUserId(String userId) {
        jdbc.update("DELETE FROM chat_messages WHERE user_id = ?", userId);
    }

    @Override
    public boolean isEmpty(String userId) {
        Integer exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM chat_messages WHERE user_id = ?)",
                Integer.class, userId);
        return exists == null || exists == 0;
    }
}
