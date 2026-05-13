package com.zoufx.ai.agent.memory;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hot Memory 的 SQLite 实现。
 *
 * - 与其他 store 共用 memoryDataSource / memoryJdbcTemplate
 * - schema 在自身 @PostConstruct 里建
 * - get / snapshot 同步：见 {@link HotMemoryStore} 接口文档（compose() 在 event loop 上不能 .block()）
 * - set 反应式：阻塞 JDBC 包到 boundedElastic
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
                CREATE TABLE IF NOT EXISTS user_profile (
                    user_id    TEXT    NOT NULL,
                    key        TEXT    NOT NULL,
                    value      TEXT    NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (user_id, key)
                )
                """);
        log.info("SqliteHotMemoryStore schema ready (user_profile)");
    }

    @Override
    public Optional<String> get(String userId, String key) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM user_profile WHERE user_id = ? AND key = ?",
                    String.class, userId, key);
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> snapshot(String userId) {
        Map<String, String> result = new HashMap<>();
        jdbc.query(
                "SELECT key, value FROM user_profile WHERE user_id = ?",
                rs -> { result.put(rs.getString("key"), rs.getString("value")); },
                userId);
        return result;
    }

    @Override
    public Mono<Void> set(String userId, String key, String value) {
        return Mono.<Void>fromRunnable(() -> setBlocking(userId, key, value))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void setBlocking(String userId, String key, String value) {
        // SQLite UPSERT 语法（3.24+）：后写覆盖前写
        jdbc.update("""
                INSERT INTO user_profile (user_id, key, value, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(user_id, key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """,
                userId, key, value, System.currentTimeMillis());
    }
}
