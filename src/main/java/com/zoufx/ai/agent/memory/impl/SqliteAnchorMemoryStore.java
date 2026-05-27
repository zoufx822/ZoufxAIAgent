package com.zoufx.ai.agent.memory.impl;

import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import com.zoufx.ai.agent.memory.model.AnchorMemoryEntry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * anchor_memory 元数据表的 SQLite 实现。
 *
 * <p>schema：
 * <pre>
 *   anchor_memory(id PK, user_id, title, summary, created_at, last_active_at)
 *   INDEX (user_id, last_active_at DESC)
 * </pre>
 *
 * <p>同步读 / 反应式写——见 {@link AnchorMemoryStore} 接口文档。
 */
@Slf4j
@Component
public class SqliteAnchorMemoryStore implements AnchorMemoryStore {

    private final JdbcTemplate jdbc;

    public SqliteAnchorMemoryStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS anchor_memory (
                    id             TEXT    PRIMARY KEY,
                    user_id        TEXT    NOT NULL,
                    title          TEXT,
                    summary        TEXT,
                    last_mood      TEXT,
                    created_at     INTEGER NOT NULL,
                    last_active_at INTEGER NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_anchor_memory_user_active ON anchor_memory(user_id, last_active_at DESC)");
        log.info("SqliteAnchorMemoryStore schema ready (anchor_memory)");
    }

    // ====== 同步读 ======

    @Override
    @Nullable
    public String findUserId(String anchorId) {
        try {
            return jdbc.queryForObject(
                    "SELECT user_id FROM anchor_memory WHERE id = ?",
                    String.class, anchorId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    @Nullable
    public Long snapshotActiveAt(String anchorId) {
        try {
            return jdbc.queryForObject(
                    "SELECT last_active_at FROM anchor_memory WHERE id = ?",
                    Long.class, anchorId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<AnchorMemoryEntry> listOtherAnchorsSync(String userId, String excludeAnchorId) {
        return jdbc.query("""
                SELECT id, user_id, title, summary, last_mood, created_at, last_active_at
                FROM anchor_memory
                WHERE user_id = ? AND id != ?
                ORDER BY last_active_at DESC
                """,
                (rs, i) -> new AnchorMemoryEntry(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("last_mood"),
                        rs.getLong("created_at"),
                        rs.getLong("last_active_at")),
                userId, excludeAnchorId);
    }

    // ====== 反应式写 ======

    @Override
    public Mono<AnchorMemoryEntry> create(String userId, @Nullable String title) {
        return Mono.fromCallable(() -> createBlocking(userId, title))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<AnchorMemoryEntry>> listByUser(String userId) {
        return Mono.fromCallable(() -> listByUserBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> touch(String anchorId, @Nullable String lastMood) {
        return Mono.<Void>fromRunnable(() -> touchBlocking(anchorId, lastMood))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateSummaryIfUnchanged(String anchorId, String summary, long snapshotAt) {
        return Mono.<Void>fromRunnable(() -> {
            int rows = jdbc.update(
                    "UPDATE anchor_memory SET summary = ? WHERE id = ? AND last_active_at = ?",
                    summary, anchorId, snapshotAt);
            if (rows == 0) {
                log.info("Summary CAS skipped [anchorId={}]: anchor was touched during compression", anchorId);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateTitleIfBlank(String anchorId, String title) {
        return Mono.<Void>fromRunnable(() ->
                // 仅当 title IS NULL 或为空白时才填——避免覆盖用户手动改过的标题
                jdbc.update("""
                        UPDATE anchor_memory
                        SET title = ?
                        WHERE id = ? AND (title IS NULL OR TRIM(title) = '')
                        """, title, anchorId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateTitle(String anchorId, String title) {
        return Mono.<Void>fromRunnable(() ->
                jdbc.update("UPDATE anchor_memory SET title = ? WHERE id = ?", title, anchorId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String anchorId) {
        return Mono.<Void>fromRunnable(() ->
                jdbc.update("DELETE FROM anchor_memory WHERE id = ?", anchorId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ====== 同步实现 ======

    private AnchorMemoryEntry createBlocking(String userId, @Nullable String title) {
        String anchorId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO anchor_memory (id, user_id, title, summary, last_mood, created_at, last_active_at)
                VALUES (?, ?, ?, NULL, NULL, ?, ?)
                """, anchorId, userId, title, now, now);
        return new AnchorMemoryEntry(anchorId, userId, title, null, null, now, now);
    }

    private List<AnchorMemoryEntry> listByUserBlocking(String userId) {
        return jdbc.query("""
                SELECT id, user_id, title, summary, last_mood, created_at, last_active_at
                FROM anchor_memory
                WHERE user_id = ?
                ORDER BY last_active_at DESC
                """,
                (rs, i) -> new AnchorMemoryEntry(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("last_mood"),
                        rs.getLong("created_at"),
                        rs.getLong("last_active_at")),
                userId);
    }

    private void touchBlocking(String anchorId, @Nullable String lastMood) {
        // 回访锚点：last_active_at 推到 now + summary 置 NULL（旧摘要作废，下次切走再压）
        // last_mood 走 COALESCE：本轮无 mood 事件时保留旧值，不被 null 覆盖
        jdbc.update("UPDATE anchor_memory SET last_active_at = ?, summary = NULL, last_mood = COALESCE(?, last_mood) WHERE id = ?",
                System.currentTimeMillis(), lastMood, anchorId);
    }
}
