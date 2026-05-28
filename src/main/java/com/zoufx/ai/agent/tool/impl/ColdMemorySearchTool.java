package com.zoufx.ai.agent.tool.impl;

import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import com.zoufx.ai.agent.memory.api.ColdMemoryStore;
import com.zoufx.ai.agent.memory.model.ColdMemoryEntry;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.zoufx.ai.agent.tool.api.ToolPrompt;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 冷内存检索工具——当对方暗示之前说过某事而当前窗口里没有时，FTS5 检索 cold_memory 原文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColdMemorySearchTool implements ToolPrompt {

    private static final int DEFAULT_LIMIT = 5;
    private static final int HARD_MAX_LIMIT = 20;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA).withZone(ZoneId.systemDefault());

    private final ColdMemoryStore coldMemoryStore;
    private final AnchorMemoryStore anchorMemoryStore;

    @Override
    public String section() {
        return "记忆检索";
    }

    @Override
    public String promptInstructions() {
        return """
                ==必须触发==：当对方暗示"之前说过/聊过/讨论过"某事而当前对话窗口里看不到时，==立刻调用 search_cold_memory==。
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

    @Tool("记忆检索：从完整记忆流里按关键词检索过往消息。当对方暗示之前说过/聊过/讨论过，而当前对话窗口里看不到时使用。返回带时间戳、角色、内容的结果列表。")
    public String search_cold_memory(
            @ToolMemoryId String memoryId,
            @P("搜索关键词（短词或短语，不要用整句话）") String keyword,
            @P("返回条数，默认 5，最大 20") int limit) {
        String userId = anchorMemoryStore.findUserId(memoryId);
        if (userId == null) {
            return "search_cold_memory 调用失败：未识别的对话上下文";
        }
        if (keyword == null || keyword.isBlank()) {
            return "search_cold_memory 调用失败：keyword 不能为空";
        }
        int effectiveLimit = limit > 0 ? Math.min(limit, HARD_MAX_LIMIT) : DEFAULT_LIMIT;

        log.info("🔎 search_cold_memory [userId={}] keyword='{}' limit={}", userId, keyword, effectiveLimit);
        List<ColdMemoryEntry> hits = coldMemoryStore.search(userId, keyword, effectiveLimit).block();
        if (hits == null || hits.isEmpty()) {
            return "经历流里没找到与「" + keyword + "」相关的内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(hits.size()).append(" 条相关经历（按相关性排序）：\n");
        int idx = 1;
        for (ColdMemoryEntry e : hits) {
            String time = TIME_FMT.format(Instant.ofEpochMilli(e.createdAt()));
            sb.append(idx++).append(". [").append(e.role()).append(" · ").append(time).append("] ")
                    .append(e.content().replace("\n", " ")).append("\n");
        }
        return sb.toString();
    }
}
