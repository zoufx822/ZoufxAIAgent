package com.zoufx.ai.agent.memory;

import com.zoufx.ai.agent.config.properties.SoulProperties;
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
 * SOUL 的 SQLite 实现。
 *
 * <p>schema 与 user_profile 不同 —— 无 user_id（SOUL 全局单例）：
 * <pre>
 *   soul_profile(key PK, value, updated_at)
 * </pre>
 *
 * <p>{@code @PostConstruct} 顺序：建表 → 检测是否为空 → 空则按 yml seed 批量 INSERT。
 * "已有就跳过" 语义防止 yml seed 误覆盖已有的管理 API 改动。
 *
 * <p>线程契约同 {@link SqliteHotMemoryStore}：get/snapshot 同步（compose 在 event loop），
 * set 反应式（boundedElastic 包阻塞 JDBC）。
 */
@Slf4j
@Component
public class SqliteSoulStore implements SoulStore {

    private final JdbcTemplate jdbc;
    private final SoulProperties properties;

    public SqliteSoulStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc,
                           SoulProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS soul_profile (
                    key        TEXT    NOT NULL PRIMARY KEY,
                    value      TEXT    NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        seedIfEmpty();
        log.info("SqliteSoulStore schema ready (soul_profile)");
    }

    /** 表为空时批量 seed yml 默认值。已有则一行不改——保护管理 API 已写入的人格。 */
    private void seedIfEmpty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM soul_profile", Integer.class);
        if (count != null && count > 0) {
            log.info("SoulStore already has {} keys, skip seeding", count);
            return;
        }
        Map<String, String> seed = properties.getSeed();
        if (seed == null || seed.isEmpty()) {
            log.info("SoulStore seed empty, nothing to insert");
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : seed.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            jdbc.update(
                    "INSERT INTO soul_profile (key, value, updated_at) VALUES (?, ?, ?)",
                    e.getKey(), e.getValue(), now);
        }
        log.info("SoulStore seeded with {} keys", seed.size());
    }

    @Override
    public Optional<String> get(String key) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM soul_profile WHERE key = ?",
                    String.class, key);
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> result = new HashMap<>();
        jdbc.query(
                "SELECT key, value FROM soul_profile",
                rs -> { result.put(rs.getString("key"), rs.getString("value")); });
        return result;
    }

    @Override
    public Mono<Void> set(String key, String value) {
        return Mono.<Void>fromRunnable(() -> setBlocking(key, value))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void setBlocking(String key, String value) {
        jdbc.update("""
                INSERT INTO soul_profile (key, value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """,
                key, value, System.currentTimeMillis());
    }
}
