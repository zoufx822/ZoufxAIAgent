package com.zoufx.ai.agent.memory.impl;

import com.zoufx.ai.agent.base.support.Blocking;
import com.zoufx.ai.agent.memory.api.AnchorMemoryDao;
import com.zoufx.ai.agent.memory.api.ChatMemoryDao;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 实现——纯落库，只实现 {@link ChatMemoryDao}。业务缓存/LC4J ChatMemoryStore 契约
 * 已上移至 {@link com.zoufx.ai.agent.memory.service.ChatMemoryService}。
 *
 * <p>隔离 key 是 anchor_id。user_id 列保留作冗余兜底，写入时通过
 * {@link AnchorMemoryDao#findUserId} 反查。anchorId 不存在视为异常状态，fail-fast 抛异常。
 *
 * <p>构造函数注入 {@link AnchorMemoryDao} 同时承担依赖排序：保证
 * {@code AnchorMemoryDaoImpl.init()} 在本类的 {@link #init()} 之前完成，
 * 因为后者的 backfill 需要 anchor 表已存在。
 *
 * <p>{@link #saveByAnchorId} 不自带事务——由调用方（{@code ChatService.persistTurn} 的外层
 * {@code memoryTxTemplate}）统一编排，JdbcTemplate 通过 DataSource 事务同步天然加入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryDaoImpl implements ChatMemoryDao {

    @Qualifier("memoryJdbcTemplate")
    private final JdbcTemplate jdbc;
    private final AnchorMemoryDao anchorMemoryDao;

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_memory (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     TEXT    NOT NULL,
                    anchor_id   TEXT    NOT NULL,
                    role        TEXT    NOT NULL,
                    content     TEXT    NOT NULL,
                    created_at  INTEGER NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_anchor ON chat_memory(anchor_id)");
        // 存量库清理：user_id 列无按其查询场景，索引冗余（列本身保留作冗余兜底，见下方 INSERT 注释）
        jdbc.execute("DROP INDEX IF EXISTS idx_chat_memory_user");
        log.info("ChatMemoryDaoImpl schema ready (chat_memory)");
    }

    @Override
    public Mono<List<ChatMessage>> loadByAnchorIdAsync(String anchorId) {
        return Blocking.call(() -> loadByAnchorId(anchorId));
    }

    @Override
    public List<ChatMessage> loadByAnchorId(String anchorId) {
        return jdbc.query(
                "SELECT content FROM chat_memory WHERE anchor_id = ? ORDER BY id ASC",
                (rs, i) -> ChatMessageDeserializer.messageFromJson(rs.getString("content")),
                anchorId);
    }

    @Override
    public void saveByAnchorId(String anchorId, List<ChatMessage> messages) {
        String userId = anchorMemoryDao.findUserId(anchorId);
        if (userId == null) {
            throw new IllegalStateException("Unknown anchorId: " + anchorId
                    + " — anchor row must exist before chat_memory writes");
        }
        long base = System.currentTimeMillis();
        jdbc.update("DELETE FROM chat_memory WHERE anchor_id = ?", anchorId);
        if (messages.isEmpty()) return;
        jdbc.batchUpdate(
                "INSERT INTO chat_memory (user_id, anchor_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ChatMessage msg = messages.get(i);
                        // 冗余兜底，当前无按 user_id 查询场景；索引已删，需要时按 user_id 建查询再补
                        ps.setString(1, userId);
                        ps.setString(2, anchorId);
                        ps.setString(3, msg.type().name());
                        ps.setString(4, ChatMessageSerializer.messageToJson(msg));
                        ps.setLong(5, base + i);
                    }

                    @Override
                    public int getBatchSize() {
                        return messages.size();
                    }
                });
    }

    @Override
    public void deleteByAnchorId(String anchorId) {
        jdbc.update("DELETE FROM chat_memory WHERE anchor_id = ?", anchorId);
    }
}
