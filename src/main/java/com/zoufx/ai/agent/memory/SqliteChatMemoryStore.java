package com.zoufx.ai.agent.memory;

import com.zoufx.ai.agent.config.properties.MemoryStoreProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 实现的 ChatMemoryStore + MemoryStore（双接口共用一套数据通路）。
 *
 * 设计要点：
 * - 单文件 SQLite + WAL 模式（并发读 + 单写）
 * - LC4J 调用 ChatMemoryStore 走框架自身调度器（与 @Tool 同情境），不影响 WebFlux event loop
 * - {@code updateMessages} 语义为"全量替换"——LC4J 每次都传完整 list，所以事务里 DELETE + 批量 INSERT
 * - 序列化用 LC4J 自带的 {@link ChatMessageSerializer} / {@link ChatMessageDeserializer}，覆盖 ChatMessage 的所有子类型
 */
@Slf4j
@Component
public class SqliteChatMemoryStore implements ChatMemoryStore, MemoryStore {

    private final String dbPath;
    private final String jdbcUrl;

    public SqliteChatMemoryStore(MemoryStoreProperties props) {
        this.dbPath = props.getDbPath();
        this.jdbcUrl = "jdbc:sqlite:" + dbPath + "?journal_mode=WAL";
    }

    @PostConstruct
    public void init() throws IOException, SQLException {
        Path path = Paths.get(dbPath);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            // 显式启用 WAL：URL 参数不一定生效，PRAGMA 是 sqlite-jdbc 文档背书的方式
            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode=WAL")) {
                String mode = rs.next() ? rs.getString(1) : "?";
                log.info("SQLite journal_mode = {}", mode);
            }
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id     TEXT    NOT NULL,
                        role        TEXT    NOT NULL,
                        content     TEXT    NOT NULL,
                        created_at  INTEGER NOT NULL
                    )
                    """);
            // v0 阶段每个 userId 数据量小，单列索引足够；ORDER BY id 走 PK，create_at 暂不需要联合索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user ON chat_messages(user_id)");
        }
        log.info("SqliteChatMemoryStore initialized: {}", dbPath);
    }

    // ====== LC4J ChatMemoryStore ======

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return loadByUserId(memoryId.toString());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        saveByUserId(memoryId.toString(), messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        deleteByUserId(memoryId.toString());
    }

    // ====== 业务 MemoryStore ======

    @Override
    public List<ChatMessage> loadByUserId(String userId) {
        String sql = "SELECT content FROM chat_messages WHERE user_id = ? ORDER BY id ASC";
        List<ChatMessage> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(ChatMessageDeserializer.messageFromJson(rs.getString("content")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("loadByUserId failed: " + userId, e);
        }
        return result;
    }

    @Override
    public void saveByUserId(String userId, List<ChatMessage> messages) {
        String del = "DELETE FROM chat_messages WHERE user_id = ?";
        String ins = "INSERT INTO chat_messages (user_id, role, content, created_at) VALUES (?, ?, ?, ?)";
        // 同批写入按毫秒+偏移单调递增，避免 v1 引入 Memory Stream 时所有 created_at 撞车
        long base = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement d = conn.prepareStatement(del)) {
                    d.setString(1, userId);
                    d.executeUpdate();
                }
                try (PreparedStatement i = conn.prepareStatement(ins)) {
                    long offset = 0;
                    for (ChatMessage msg : messages) {
                        i.setString(1, userId);
                        i.setString(2, msg.type().name());
                        i.setString(3, ChatMessageSerializer.messageToJson(msg));
                        i.setLong(4, base + offset++);
                        i.addBatch();
                    }
                    i.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("saveByUserId failed: " + userId, e);
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        String sql = "DELETE FROM chat_messages WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("deleteByUserId failed: " + userId, e);
        }
    }

    @Override
    public boolean isEmpty(String userId) {
        String sql = "SELECT 1 FROM chat_messages WHERE user_id = ? LIMIT 1";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("isEmpty failed: " + userId, e);
        }
    }
}
