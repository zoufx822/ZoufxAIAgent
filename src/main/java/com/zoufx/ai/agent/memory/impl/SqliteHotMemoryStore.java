package com.zoufx.ai.agent.memory.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hot Memory 的 SQLite 实现。
 *
 * <p>schema：
 * <pre>
 *   hot_memory(user_id, type, key, value, updated_at)  PK=(user_id, type, key)
 * </pre>
 *
 * <p>type 维度让 hot_memory 容纳多种"重要记忆"子类：user-impression / significant-event / commitment。
 *
 * <p>- 与其他 store 共用 memoryDataSource / memoryJdbcTemplate
 * <p>- schema 在自身 @PostConstruct 里建
 * <p>- snapshot / fetchValues 同步：见 {@link HotMemoryStore} 接口文档
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
    public Map<String, String> snapshot(String userId, String type) {
        // LinkedHashMap + ORDER BY updated_at DESC：保证最新写入的在前。
        // user-impression 按 key 取值不依赖顺序；append-only 的 significant-event /
        // commitment 则靠此顺序让前端直接取「最近 N 条」，无需自行排序（key 是无时间语义的 UUID）。
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT key, value FROM hot_memory WHERE user_id = ? AND type = ? "
                        + "ORDER BY updated_at DESC, key DESC",
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
    public Map<String, String> fetchValues(String userId, String type, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        List<Object> args = new ArrayList<>(keys.size() + 2);
        args.add(userId);
        args.add(type);
        args.addAll(keys);
        Map<String, String> out = new HashMap<>();
        jdbc.query(
                "SELECT key, value FROM hot_memory WHERE user_id = ? AND type = ? AND key IN (" + placeholders + ")",
                rs -> { out.put(rs.getString("key"), rs.getString("value")); },
                args.toArray());
        return out;
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
