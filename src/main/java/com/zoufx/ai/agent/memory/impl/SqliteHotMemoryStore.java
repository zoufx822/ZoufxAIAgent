package com.zoufx.ai.agent.memory.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hot Memory 的 SQLite 实现（v0.13 加 type 列）。
 *
 * <p>schema：
 * <pre>
 *   hot_memory(user_id, type, key, value, updated_at)  PK=(user_id, type, key)
 * </pre>
 *
 * <p>type 维度让 hot_memory 容纳多种"重要记忆"子类：user-impression / significant-event / ...
 * v0.13 仅 user-impression 被使用，schema 为未来扩展留好骨架。
 *
 * <p>- 与其他 store 共用 memoryDataSource / memoryJdbcTemplate
 * <p>- schema 在自身 @PostConstruct 里建
 * <p>- get / snapshot 同步：见 {@link HotMemoryStore} 接口文档
 * <p>- set 反应式：阻塞 JDBC 包到 boundedElastic
 */
@Slf4j
@Component
public class SqliteHotMemoryStore implements HotMemoryStore {

    private final JdbcTemplate jdbc;

    public SqliteHotMemoryStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hot_memory (
                    user_id    TEXT    NOT NULL,
                    type       TEXT    NOT NULL,
                    key        TEXT    NOT NULL,
                    value      TEXT    NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (user_id, type, key)
                )
                """);
        log.info("SqliteHotMemoryStore schema ready (hot_memory with type)");
    }

    @Override
    public Optional<String> get(String userId, String type, String key) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM hot_memory WHERE user_id = ? AND type = ? AND key = ?",
                    String.class, userId, type, key);
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> snapshot(String userId, String type) {
        Map<String, String> result = new HashMap<>();
        jdbc.query(
                "SELECT key, value FROM hot_memory WHERE user_id = ? AND type = ?",
                rs -> { result.put(rs.getString("key"), rs.getString("value")); },
                userId, type);
        return result;
    }

    @Override
    public Mono<Void> set(String userId, String type, String key, String value) {
        return Mono.<Void>fromRunnable(() -> setBlocking(userId, type, key, value))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<HotMemoryEntry> recent(String userId, String type, int limit) {
        return jdbc.query(
                "SELECT key, value, updated_at FROM hot_memory "
                        + "WHERE user_id = ? AND type = ? "
                        + "ORDER BY updated_at DESC LIMIT ?",
                (rs, rowNum) -> new HotMemoryEntry(
                        rs.getString("key"),
                        rs.getString("value"),
                        rs.getLong("updated_at")),
                userId, type, limit);
    }

    private void setBlocking(String userId, String type, String key, String value) {
        // SQLite UPSERT 语法（3.24+）：后写覆盖前写
        jdbc.update("""
                INSERT INTO hot_memory (user_id, type, key, value, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(user_id, type, key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """,
                userId, type, key, value, System.currentTimeMillis());
    }
}
