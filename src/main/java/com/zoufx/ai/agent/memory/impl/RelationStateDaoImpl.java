package com.zoufx.ai.agent.memory.impl;

import com.zoufx.ai.agent.memory.api.RelationStateDao;
import com.zoufx.ai.agent.memory.model.RelationState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * relation_state 的 SQLite 实现——每用户一行，持久化关系温度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationStateDaoImpl implements RelationStateDao {

    @Qualifier("memoryJdbcTemplate")
    private final JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS relation_state (
                    user_id     TEXT    PRIMARY KEY,
                    familiarity REAL    NOT NULL DEFAULT 0,
                    trust       REAL    NOT NULL DEFAULT 0,
                    warmth      REAL    NOT NULL DEFAULT 0,
                    updated_at  INTEGER NOT NULL
                )
                """);
        log.info("RelationStateDaoImpl schema ready (relation_state)");
    }

    @Override
    public RelationState load(String userId) {
        try {
            return jdbc.queryForObject(
                    "SELECT familiarity, trust, warmth, updated_at FROM relation_state WHERE user_id = ?",
                    (rs, i) -> new RelationState(
                            rs.getDouble("familiarity"),
                            rs.getDouble("trust"),
                            rs.getDouble("warmth"),
                            rs.getLong("updated_at")),
                    userId);
        } catch (EmptyResultDataAccessException e) {
            return RelationState.EMPTY;
        }
    }

    @Override
    public void save(String userId, RelationState state) {
        jdbc.update("""
                INSERT INTO relation_state (user_id, familiarity, trust, warmth, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    familiarity = excluded.familiarity,
                    trust       = excluded.trust,
                    warmth      = excluded.warmth,
                    updated_at  = excluded.updated_at
                """, userId, state.familiarity(), state.trust(), state.warmth(), state.updatedAt());
    }
}
