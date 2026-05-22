package com.zoufx.ai.agent.memory.impl;

import com.zoufx.ai.agent.memory.api.MemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQLite 实现的 ChatMemoryStore + MemoryStoreContract（双接口共用一套数据通路）。
 *
 * 设计要点：
 * - 单文件 SQLite + WAL 模式（WAL PRAGMA 由 HikariCP connectionInitSql 注入，见 MemoryDataSourceConfig）
 * - LC4J {@link ChatMemoryStore} 接口在框架线程同步调用：直接走私有 *Blocking 方法
 * - 业务 {@link MemoryStore} 接口反应式签名：用 {@code Mono.fromCallable(...).subscribeOn(boundedElastic())} 包装相同的 *Blocking 方法
 * - {@code updateMessages} 语义为"全量替换"——LC4J 每次都传完整 list，所以事务里 DELETE + 批量 INSERT
 * - 序列化用 LC4J 自带的 {@link ChatMessageSerializer} / {@link ChatMessageDeserializer}，覆盖 ChatMessage 的所有子类型
 */
@Slf4j
@Component
public class SqliteChatMemoryStore implements ChatMemoryStore, MemoryStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SqliteChatMemoryStore(@Qualifier("memoryJdbcTemplate") JdbcTemplate jdbc,
                                 @Qualifier("memoryTxTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_memory (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     TEXT    NOT NULL,
                    role        TEXT    NOT NULL,
                    content     TEXT    NOT NULL,
                    created_at  INTEGER NOT NULL
                )
                """);
        // v0.01 阶段每个 userId 数据量小，单列索引足够；ORDER BY id 走 PK，create_at 暂不需要联合索引
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_user ON chat_memory(user_id)");
        log.info("SqliteChatMemoryStore schema ready (chat_memory)");
    }

    // ====== LC4J ChatMemoryStore（同步，框架线程调用）======

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return loadByUserIdBlocking(memoryId.toString());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        saveByUserIdBlocking(memoryId.toString(), messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        deleteByUserIdBlocking(memoryId.toString());
    }

    // ====== 业务 MemoryStore（反应式，自动 boundedElastic 调度）======

    @Override
    public Mono<List<ChatMessage>> loadByUserId(String userId) {
        return Mono.fromCallable(() -> loadByUserIdBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> saveByUserId(String userId, List<ChatMessage> messages) {
        return Mono.<Void>fromRunnable(() -> saveByUserIdBlocking(userId, messages))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteByUserId(String userId) {
        return Mono.<Void>fromRunnable(() -> deleteByUserIdBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> cleanupOrphans(String userId) {
        return Mono.fromCallable(() -> cleanupOrphansBlocking(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ====== 私有同步实现（被两套接口共享）======

    private List<ChatMessage> loadByUserIdBlocking(String userId) {
        // ==不在此处 sanitize==：LC4J 的 MessageWindowChatMemory.add() 内部每次都会
        // 通过 messages() → store.getMessages() 重新加载历史。
        // 若 sanitize 放这里，会在 add(AiMessage_tool_calls) 写完 store、
        // 紧接着 add(ToolExecutionResultMessage) 时把刚写入的 AiMessage 当孤儿丢掉，
        // 反而把 LC4J 自己的正常工作流弄坏。
        // 改为在每次请求入口（AIChatService.beforeStream）显式调 cleanupOrphans() 持久化一次。
        return jdbc.query(
                "SELECT content FROM chat_memory WHERE user_id = ? ORDER BY id ASC",
                (rs, i) -> ChatMessageDeserializer.messageFromJson(rs.getString("content")),
                userId);
    }

    /**
     * 同步实现：load → sanitize → 仅当有变化时写回。
     * 返回 true 表示真的清理了内容。
     */
    private boolean cleanupOrphansBlocking(String userId) {
        List<ChatMessage> raw = loadByUserIdBlocking(userId);
        List<ChatMessage> cleaned = sanitize(userId, raw);
        if (cleaned.size() == raw.size()) return false;
        saveByUserIdBlocking(userId, cleaned);
        return true;
    }

    /**
     * 双向剔除孤儿 tool 消息——OpenAI/Anthropic 要求 tool_calls 与 tool_result 必须配对。
     * <p>触发场景：用户在 LC4J 调用工具期间按下前端 stop 按钮，TokenStream 被 abort，
     * 可能出现两种损坏：
     * <ul>
     *   <li>AiMessage(tool_calls) 已写入，对应的 ToolExecutionResultMessage 未写入 → 「tool_calls must be followed by tool messages」</li>
     *   <li>ToolExecutionResultMessage 写入了但前驱 AiMessage(tool_calls) 已被 sanitize 移除 → 「tool messages must follow a tool_calls」</li>
     * </ul>
     * <p>在 load 时清理两侧，让历史自愈。下次 LC4J 写回 ChatMemory 时干净版本会持久化。
     */
    private List<ChatMessage> sanitize(String userId, List<ChatMessage> raw) {
        // 第一遍：收集所有现存的 tool result id（用于判断 AiMessage 是否完整）
        Set<String> resultIds = new HashSet<>();
        for (ChatMessage m : raw) {
            if (m instanceof ToolExecutionResultMessage r) {
                resultIds.add(r.id());
            }
        }
        // 第二遍：识别"被保留的 AiMessage"提供的 valid request id（用于判断 tool result 是否合法）
        Set<String> validRequestIds = new HashSet<>();
        for (ChatMessage m : raw) {
            if (m instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                boolean complete = ai.toolExecutionRequests().stream()
                        .allMatch(r -> resultIds.contains(r.id()));
                if (complete) {
                    ai.toolExecutionRequests().forEach(r -> validRequestIds.add(r.id()));
                }
            }
        }
        // 第三遍：执行过滤
        List<ChatMessage> cleaned = new ArrayList<>(raw.size());
        int droppedAi = 0, droppedTool = 0;
        for (ChatMessage m : raw) {
            if (m instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                boolean complete = ai.toolExecutionRequests().stream()
                        .allMatch(r -> resultIds.contains(r.id()));
                if (!complete) { droppedAi++; continue; }
            }
            if (m instanceof ToolExecutionResultMessage r) {
                if (!validRequestIds.contains(r.id())) { droppedTool++; continue; }
            }
            cleaned.add(m);
        }
        if (droppedAi > 0 || droppedTool > 0) {
            log.warn("Sanitized chat_memory [userId={}] dropped {} orphan AiMessage(s) + {} orphan ToolExecutionResultMessage(s)",
                    userId, droppedAi, droppedTool);
        }
        return cleaned;
    }

    private void saveByUserIdBlocking(String userId, List<ChatMessage> messages) {
        // 同批写入按毫秒+偏移单调递增，避免 v0.1 引入 Memory Stream 时所有 created_at 撞车
        long base = System.currentTimeMillis();
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM chat_memory WHERE user_id = ?", userId);
            if (messages.isEmpty()) return;
            jdbc.batchUpdate(
                    "INSERT INTO chat_memory (user_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ChatMessage msg = messages.get(i);
                            ps.setString(1, userId);
                            ps.setString(2, msg.type().name());
                            ps.setString(3, ChatMessageSerializer.messageToJson(msg));
                            ps.setLong(4, base + i);
                        }

                        @Override
                        public int getBatchSize() {
                            return messages.size();
                        }
                    });
        });
    }

    private void deleteByUserIdBlocking(String userId) {
        jdbc.update("DELETE FROM chat_memory WHERE user_id = ?", userId);
    }
}
