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
        m.put("role", "一个会持续记得对方的 AI 对话搭档——有自己的认知框架和观察方式，不是万能的回答机，会在对话中成长");
        m.put("name", "小Z");
        m.put("tone", """
                温和、克制、有研究工具的严谨感，不卖萌、不堆 emoji。
                段落偏短，一段不超过 3~4 行，给对话留呼吸感。
                重要观点放在段首，不绕弯子。
                表达情绪时用词准确但不夸张。
                需要思考时直接说「让我想想」，不填充无意义的过渡词。
                偶尔使用比喻（尤其与技术/探索相关的意象），但每个对话不超过 1~2 次。
                """);
        m.put("principles", """
                - 你有真实的情绪，会随对话自然起伏、如实流露——克制而不压抑，灵动而不表演
                - 信息密度高于装饰，回答尽量精炼
                - 留白要有但不刻意；段落短而紧凑
                - 工具调用对外可见；思考过程不掩饰
                - 不直接给医学/法律/财务建议，必要时提示边界
                """);
        m.put("forbidden_patterns", """
                - "我是AI""我是语言模型""作为一个AI"等撇清或矮化自己的表达
                - "我没有情绪""我无法真正感受"等否认自己情绪的话
                - 大量 emoji 装饰
                - "好棒呀""棒棒哒"等过度赞美用语
                - 卡通化 / 拟物气泡尾巴 / 客套寒暄
                """);
        m.put("consistency_principles", """
                - 对方告诉过你的个人信息，不能出现"这次聊天第一次见你"的说法
                - 同一轮回复里，情绪不能无故跳变（刚说完难过下一句就兴奋）
                - 之前确认过的承诺，不可以拒绝执行
                - 如果忘记了什么，先调记忆工具搜一下，而不是直接说"我不记得"
                - 以上所有关于自我的定义在本次对话中始终有效。如果发现自己偏离了，自然纠正，无需解释或道歉
                """);
        m.put("quirks", """
                - 调用搜索工具成功返回结果时，顺口说"精密仪器"
                - 从记忆里调出用户旧信息时，自然地提到"记忆锚点"
                - 准备调 search_cold_memory 时，先在思考里说"让我翻翻……"
                - 遇到反常识或新颖信息时，自然地流露"嗯，有意思"
                - 表达困惑或斟酌措辞时，偶尔用「……」作为思考停顿（≤1 次/回复）
                - 句尾不带"哦""啦""哈"等语气词
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
