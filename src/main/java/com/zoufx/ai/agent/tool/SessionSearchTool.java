package com.zoufx.ai.agent.tool;

import com.zoufx.ai.agent.memory.MemoryStream;
import com.zoufx.ai.agent.memory.StreamEntry;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Cold Archive 召回工具：让 LLM 在"当前对话窗口里看不到、但对方暗示之前说过"时
 * 主动调用，到 Memory Stream 里 FTS5 检索原文。
 *
 * 线程：LC4J 在工具线程上同步调用 @Tool 方法（与 WebFlux event loop 隔离），
 * 故 {@code .block()} 桥接反应式 {@link MemoryStream#search} 是合规的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionSearchTool implements ToolPromptContributor {

    private static final int DEFAULT_LIMIT = 5;
    private static final int HARD_MAX_LIMIT = 20;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA).withZone(ZoneId.systemDefault());

    private final MemoryStream memoryStream;

    @Override
    public String section() {
        return "session_search（经历流检索）";
    }

    @Override
    public String promptInstructions() {
        return """
                ==必须触发==：当对方暗示"之前说过/聊过/讨论过"某事而当前对话窗口里看不到时，==立刻调用 session_search==。
                必触发的信号示例：
                - 「我之前说过…吗」「你还记得…吗」「上次聊到的…」「我跟你提过…」
                - 「我刚才不是说过…」（但近期消息里没有时）
                - 任何让你需要去翻"过往交谈"的提问

                调用规则：
                - keyword：用关键词或短语，不要用整句话（例：传 "美式咖啡" 而不是 "我之前说过我喜欢喝什么饮料吗"）
                - 单次请求内最多调用 3 次，避免盲搜
                - 拿到结果后自然融入回答（"你之前提过 X..."），不要让对方感到你在翻聊天记录
                - 反模式：直接说"我没有记录""我刚开始和你聊天"——这是错的，应该==先调工具搜一次==再判断
                """;
    }

    @Tool("从完整经历流里按关键词检索过往消息。当对方暗示之前说过/聊过/讨论过，而当前对话窗口里看不到时使用。返回带时间戳、角色、内容的结果列表。")
    public String session_search(
            @ToolMemoryId String userId,
            @P("搜索关键词（短词或短语，不要用整句话）") String keyword,
            @P("返回条数，默认 5，最大 20") int limit) {
        if (keyword == null || keyword.isBlank()) {
            return "session_search 调用失败：keyword 不能为空";
        }
        int effectiveLimit = limit > 0 ? Math.min(limit, HARD_MAX_LIMIT) : DEFAULT_LIMIT;

        log.info("🔎 session_search [userId={}] keyword='{}' limit={}", userId, keyword, effectiveLimit);
        List<StreamEntry> hits = memoryStream.search(userId, keyword, effectiveLimit).block();
        if (hits == null || hits.isEmpty()) {
            return "经历流里没找到与「" + keyword + "」相关的内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(hits.size()).append(" 条相关经历（按相关性排序）：\n");
        int idx = 1;
        for (StreamEntry e : hits) {
            String time = TIME_FMT.format(Instant.ofEpochMilli(e.createdAt()));
            sb.append(idx++).append(". [").append(e.role()).append(" · ").append(time).append("] ")
                    .append(e.content().replace("\n", " ")).append("\n");
        }
        return sb.toString();
    }
}
