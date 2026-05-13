package com.zoufx.ai.agent.memory;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Memory Stream 的 SQLite + FTS5 实现。
 *
 * 设计要点：
 * - 与 SqliteChatMemoryStore 共用 memoryDataSource / memoryJdbcTemplate（HikariCP + WAL）
 * - 表 / 虚表 / 触发器在自身 {@code @PostConstruct} 里建——每个 store 类自管自己的 schema，避免 schema-bootstrap 全局类
 *
 * FTS5 中文分词处理：
 * - {@code unicode61} 默认会把一整段连续 CJK 视作==单个 token==（"美式咖啡" → 1 token），导致子串查询"美式"无法命中
 * - 解决：写入 FTS 索引时由 Java 侧按 codepoint 把内容打散成空格分隔的字符序列
 *   （原文 "我喜欢喝美式咖啡" → FTS 内容 "我 喜 欢 喝 美 式 咖 啡"）
 * - 查询时同样把 keyword 打散，并用 FTS5 phrase 语法 {@code "c1 c2 ..."} 保证相邻顺序
 * - FTS 索引存的是==分词版本==的内容，与主表 memory_stream.content 的==原文==分离；
 *   故 FTS 不采用 external content 模式，而是各自存储（FTS 表只是索引，体积小，重复存储可接受）
 * - 主表与 FTS 表写入在同一事务里，借 {@code last_insert_rowid()} 拿主表自增 id 当 FTS rowid，保证一一对应
 */
@Slf4j
@Component
public class SqliteMemoryStream implements MemoryStream {

    private static final int SEARCH_LIMIT_HARD_MAX = 20;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SqliteMemoryStream(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc,
                              @Qualifier("memoryTxTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS memory_stream (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id    TEXT    NOT NULL,
                    role       TEXT    NOT NULL,
                    content    TEXT    NOT NULL,
                    metadata   TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stream_user_time ON memory_stream(user_id, created_at)");

        // FTS5 表：独立存储分词版本的内容（不用 external content 模式）
        jdbc.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS memory_stream_fts USING fts5(
                    content,
                    tokenize='unicode61'
                )
                """);

        // DELETE 触发器：主表行删了，FTS 索引同步删（rowid 对齐）
        jdbc.execute("""
                CREATE TRIGGER IF NOT EXISTS mstream_ad AFTER DELETE ON memory_stream BEGIN
                    DELETE FROM memory_stream_fts WHERE rowid = old.id;
                END
                """);
        // INSERT 触发器不再使用——FTS 内容是分词版本，需要 Java 侧预处理，无法在 SQL 触发器里完成

        log.info("SqliteMemoryStream schema ready (memory_stream + FTS5 with codepoint-split tokenization)");
    }

    @Override
    public Mono<Void> append(String userId, String role, String content, String metadataJson) {
        return Mono.<Void>fromRunnable(() -> appendBlocking(userId, role, content, metadataJson))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<StreamEntry>> search(String userId, String keyword, int limit) {
        return Mono.fromCallable(() -> searchBlocking(userId, keyword, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<StreamEntry>> loadRecent(String userId, int limit) {
        return Mono.fromCallable(() -> loadRecentBlocking(userId, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ====== 私有同步实现 ======

    private void appendBlocking(String userId, String role, String content, String metadataJson) {
        String tokenized = codepointSplit(content);
        // 同事务保证 last_insert_rowid 与刚 INSERT 的主表行对齐，且失败时两条都回滚
        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO memory_stream (user_id, role, content, metadata, created_at) VALUES (?, ?, ?, ?, ?)",
                    userId, role, content, metadataJson, System.currentTimeMillis());
            jdbc.update(
                    "INSERT INTO memory_stream_fts (rowid, content) SELECT last_insert_rowid(), ?",
                    tokenized);
        });
    }

    private List<StreamEntry> searchBlocking(String userId, String keyword, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), SEARCH_LIMIT_HARD_MAX);
        // 把 keyword 也按 codepoint 切，包成 FTS5 phrase 查询：保证 token 相邻顺序匹配，
        // 同时双引号外层包裹让特殊字符（* - ( ) 等）失去 FTS5 元义、按字面匹配
        String tokenized = codepointSplit(keyword);
        if (tokenized.isEmpty()) return List.of();
        String phrase = "\"" + tokenized.replace("\"", "\"\"") + "\"";
        return jdbc.query("""
                        SELECT ms.id, ms.role, ms.content, ms.created_at
                        FROM memory_stream_fts
                        JOIN memory_stream ms ON memory_stream_fts.rowid = ms.id
                        WHERE memory_stream_fts MATCH ? AND ms.user_id = ?
                        ORDER BY rank
                        LIMIT ?
                        """,
                (rs, i) -> new StreamEntry(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getLong("created_at")),
                phrase, userId, safeLimit);
    }

    private List<StreamEntry> loadRecentBlocking(String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), SEARCH_LIMIT_HARD_MAX);
        return jdbc.query(
                "SELECT id, role, content, created_at FROM memory_stream WHERE user_id = ? ORDER BY id DESC LIMIT ?",
                (rs, i) -> new StreamEntry(
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
