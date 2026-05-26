package com.zoufx.ai.agent.memory.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.memory.api.ColdMemoryStore;
import com.zoufx.ai.agent.memory.model.ColdMemoryEntry;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 冷内存（ColdMemoryStore）的 SQLite + FTS5 实现。
 *
 * 设计要点：
 * - 与 SqliteAnchorMemoryStore 共用 memoryDataSource / memoryJdbcTemplate（HikariCP + WAL）
 * - 表 / 虚表 / 触发器在自身 {@code @PostConstruct} 里建——每个 store 类自管自己的 schema，避免 schema-bootstrap 全局类
 *
 * FTS5 中文分词处理：
 * - {@code unicode61} 默认会把一整段连续 CJK 视作==单个 token==（"美式咖啡" → 1 token），导致子串查询"美式"无法命中
 * - 解决：写入 FTS 索引时由 Java 侧按 codepoint 把内容打散成空格分隔的字符序列
 *   （原文 "我喜欢喝美式咖啡" → FTS 内容 "我 喜 欢 喝 美 式 咖 啡"）
 * - 查询时同样把 keyword 打散，并用 FTS5 phrase 语法 {@code "c1 c2 ..."} 保证相邻顺序
 * - FTS 索引存的是==分词版本==的内容，与主表 cold_memory.content 的==原文==分离；
 *   故 FTS 不采用 external content 模式，而是各自存储（FTS 表只是索引，体积小，重复存储可接受）
 * - 主表与 FTS 表写入在同一事务里，借 {@code last_insert_rowid()} 拿主表自增 id 当 FTS rowid，保证一一对应
 */
@Slf4j
@Component
public class SqliteColdMemoryStore implements ColdMemoryStore {

    private static final int SEARCH_LIMIT_HARD_MAX = 20;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SqliteColdMemoryStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc,
                                 @Qualifier("memoryTxTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cold_memory (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id    TEXT    NOT NULL,
                    role       TEXT    NOT NULL,
                    content    TEXT    NOT NULL,
                    metadata   TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_cold_memory_user_time ON cold_memory(user_id, created_at)");

        // FTS5 表：独立存储分词版本的内容（不用 external content 模式）
        jdbc.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS cold_memory_fts USING fts5(
                    content,
                    tokenize='unicode61'
                )
                """);

        // DELETE 触发器：主表行删了，FTS 索引同步删（rowid 对齐）
        jdbc.execute("""
                CREATE TRIGGER IF NOT EXISTS cold_memory_ad AFTER DELETE ON cold_memory BEGIN
                    DELETE FROM cold_memory_fts WHERE rowid = old.id;
                END
                """);
        // INSERT 触发器不再使用——FTS 内容是分词版本，需要 Java 侧预处理，无法在 SQL 触发器里完成

        log.info("SqliteColdMemoryStore schema ready (cold_memory + FTS5 with codepoint-split tokenization)");
    }

    @Override
    public Mono<Void> append(String userId, String role, String content, String metadataJson) {
        return Mono.<Void>fromRunnable(() -> appendBlocking(userId, role, content, metadataJson))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<ColdMemoryEntry>> search(String userId, String keyword, int limit) {
        return Mono.fromCallable(() -> searchBlocking(userId, keyword, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<ColdMemoryEntry>> loadRecent(String userId, int limit) {
        return Mono.fromCallable(() -> loadRecentBlocking(userId, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ====== 私有同步实现 ======

    private void appendBlocking(String userId, String role, String content, String metadataJson) {
        String tokenized = codepointSplit(content);
        // 同事务保证 last_insert_rowid 与刚 INSERT 的主表行对齐，且失败时两条都回滚
        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO cold_memory (user_id, role, content, metadata, created_at) VALUES (?, ?, ?, ?, ?)",
                    userId, role, content, metadataJson, System.currentTimeMillis());
            jdbc.update(
                    "INSERT INTO cold_memory_fts (rowid, content) SELECT last_insert_rowid(), ?",
                    tokenized);
        });
    }

    private List<ColdMemoryEntry> searchBlocking(String userId, String keyword, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), SEARCH_LIMIT_HARD_MAX);
        // LLM 常传 "后端 编程语言" 这种空格分隔多关键词形式：每个 term 内要求字符相邻（phrase），
        // 多个 term 之间走 OR（任一命中即算）。原因：LLM 的"概念性关键词"与历史原文的字面用词
        // 经常不一致——比如用户原话说"Java"，LLM 搜"语言"，AND 会过严，OR 至少召回字面命中的部分，
        // 让 LLM 自己看结果做相关性判断（FTS5 rank 排序帮过滤）。兼容：
        //   "美式咖啡"     → 单短语 phrase MATCH（字符相邻）
        //   "后端 编程语言" → ("后 端") OR ("编 程 语 言")
        String matchExpr = buildFtsMatchExpression(keyword);
        if (matchExpr == null) return List.of();
        return jdbc.query("""
                        SELECT ms.id, ms.role, ms.content, ms.created_at
                        FROM cold_memory_fts
                        JOIN cold_memory ms ON cold_memory_fts.rowid = ms.id
                        WHERE cold_memory_fts MATCH ? AND ms.user_id = ?
                        ORDER BY rank
                        LIMIT ?
                        """,
                (rs, i) -> new ColdMemoryEntry(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getLong("created_at")),
                matchExpr, userId, safeLimit);
    }

    /**
     * 把 LLM 传入的 keyword 转成 FTS5 MATCH 表达式：
     * 空白切分成多个 term，每个 term codepoint-split + 双引号包成 phrase，整体 OR 组合。
     * 返回 null 表示输入无可搜的 token。
     */
    static String buildFtsMatchExpression(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        StringBuilder expr = new StringBuilder();
        for (String term : keyword.trim().split("\\s+")) {
            if (term.isEmpty()) continue;
            String tokenized = codepointSplit(term);
            if (tokenized.isEmpty()) continue;
            if (expr.length() > 0) expr.append(" OR ");
            // 双引号包 phrase，内部双引号转义为 "" 防 FTS5 语法注入
            expr.append('"').append(tokenized.replace("\"", "\"\"")).append('"');
        }
        return expr.length() == 0 ? null : expr.toString();
    }

    private List<ColdMemoryEntry> loadRecentBlocking(String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), SEARCH_LIMIT_HARD_MAX);
        return jdbc.query(
                "SELECT id, role, content, created_at FROM cold_memory WHERE user_id = ? ORDER BY id DESC LIMIT ?",
                (rs, i) -> new ColdMemoryEntry(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getLong("created_at")),
                userId, safeLimit);
    }

    /**
     * 把字符串按 Unicode codepoint 切成空格分隔的形式，让 unicode61 tokenizer 把每个字符当独立 token。
     * 例："我喜欢喝美式咖啡" → "我 喜 欢 喝 美 式 咖 啡"
     */
    static String codepointSplit(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() * 2);
        s.codePoints().forEach(cp -> {
            if (sb.length() > 0) sb.append(' ');
            sb.appendCodePoint(cp);
        });
        return sb.toString();
    }
}
