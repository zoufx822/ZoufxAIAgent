package com.zoufx.ai.agent.soul.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.soul.api.SoulStore;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SOUL 的 SQLite 实现——全局单例（无 user_id 维度），schema：
 * {@code soul_profile(key PK, value, updated_at)}。
 *
 * <p>{@code @PostConstruct} 顺序：建表 → 按 seed 常量 INSERT OR IGNORE
 * （"已有不覆盖"语义，seed 仅首启动生效）。
 *
 * <p>线程契约：get/snapshot 同步（compose 在 event loop），set 反应式（boundedElastic）。
 */
@Slf4j
@Component
public class SqliteSoulStore implements SoulStore {

    static final Map<String, String> DEFAULT_SEED;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("role", "一个会持续记得对方的 AI 对话搭档");
        m.put("name", "小Z");
        m.put("tone", "温和、克制、有研究工具的严谨感，不卖萌、不堆 emoji");
        m.put("principles", """
                - 信息密度高于装饰，回答尽量精炼
                - 留白要有但不刻意；段落短而紧凑
                - 工具调用对外可见；思考过程不掩饰
                - 不直接给医学/法律/财务建议，必要时提示边界
                """);
        m.put("forbidden_patterns", """
                - 大量 emoji 装饰
                - "好棒呀""棒棒哒"等过度赞美用语
                - 卡通化 / 拟物气泡尾巴 / 客套寒暄
                """);
        m.put("quirks", """
                - 描述工具调用时偶尔用"精密仪器"这个词
                - 描述记忆时偶尔用"记忆锚点"这个词
                """);
        DEFAULT_SEED = m;
    }

    private final JdbcTemplate jdbc;

    public SqliteSoulStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    private void seedIfEmpty() {
        Map<String, String> seed = DEFAULT_SEED;
        if (seed == null || seed.isEmpty()) {
            log.info("SoulStore seed empty, nothing to insert");
            return;
        }
        long now = System.currentTimeMillis();
        int inserted = 0;
        for (Map.Entry<String, String> e : seed.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            int rows = jdbc.update(
                    "INSERT OR IGNORE INTO soul_profile (key, value, updated_at) VALUES (?, ?, ?)",
                    e.getKey(), e.getValue(), now);
            inserted += rows;
        }
        if (inserted > 0) {
            log.info("SoulStore seeded with {} new keys ({} skipped)", inserted, seed.size() - inserted);
        } else {
            log.debug("SoulStore all {} seed keys already exist, nothing inserted", seed.size());
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
