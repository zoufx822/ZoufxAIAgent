package com.zoufx.ai.agent.chat.support;

import com.zoufx.ai.agent.chat.model.ChatEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.FluxSink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 有状态的情绪事件处理器——从 LLM content 流里剥离 {@code <!--mood:KEYWORD-->} 标记。
 *
 * <p>工作方式：维护一个 buffer，每次 {@link #accept(String)} 时追加 token，
 * 扫描是否含完整 mood 标记，命中即剥离并发独立 SSE mood 事件。
 * 为容忍标记跨 chunk，命中前保留 buffer 末尾 {@code tailSize} 字符不 emit。
 * 命中后剩余内容直接全 emit（mood 一轮只发一次，无需再留尾）。
 *
 * <p>一次性使用：==每条 chat 请求 new 一个实例==，complete 时调 {@link #flush()} 兜底扫描 + 清空。
 *
 * <p>线程模型：accept / flush 都在 LC4J 单 token 回调线程上串行调用，无并发——不需要 synchronized。
 */
@Slf4j
public class MoodEventProcessor {

    /** 匹配 {@code <!--mood:任何非 > 字符-->}，非贪婪。 */
    private static final Pattern MOOD = Pattern.compile("<!--mood:([^>]+?)-->");

    private final int tailSize;
    private final FluxSink<ChatEvent> sink;
    private final String userId;
    private final StringBuilder buffer = new StringBuilder();
    /** 本轮最后一次命中的 mood 关键词；流末由 ChatService 取出落库到 anchor.last_mood / cold_memory.mood。 */
    @Nullable private String lastMood;

    public MoodEventProcessor(int tailSize, FluxSink<ChatEvent> sink, String userId) {
        this.tailSize = tailSize;
        this.sink = sink;
        this.userId = userId;
    }

    /** 接收 LC4J 增量 token；可能 emit content + 0..N mood 事件（一轮 LLM 可能输出多次标记）。 */
    public void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        buffer.append(chunk);
        scanAndStrip();
        flushSafePrefix();
    }

    /** 流末尾兜底：再扫一次（标记可能正好在末尾）+ flush 全部剩余内容。 */
    public void flush() {
        scanAndStrip();
        if (buffer.length() > 0) {
            sink.next(new ChatEvent("content", buffer.toString()));
            buffer.setLength(0);
        }
    }

    /**
     * 扫描并剥离 buffer 内的所有 mood 标记。命中：emit 命中前内容 + 删除标记 + 发 mood 事件。
     * <p>用 while 循环处理"一轮 LLM 输出多次 mood 标记"的情况（例如开头一次 + 末尾一次）。
     * 前端 setMood 自然以后到为准，并通过 React key 重放 fade-in 动画。
     */
    private void scanAndStrip() {
        while (true) {
            Matcher m = MOOD.matcher(buffer);
            if (!m.find()) return;
            String mood = m.group(1).trim();
            String before = buffer.substring(0, m.start());
            if (!before.isEmpty()) {
                sink.next(new ChatEvent("content", before));
            }
            buffer.delete(0, m.end());
            sink.next(new ChatEvent("mood", moodPayload(mood)));
            lastMood = mood;
            log.info("Mood stripped [userId={}] mood={}", userId, mood);
        }
    }

    /** 本轮最后一次 mood，无则 null。一轮 LLM 多次 mood 标记时以最后一次为准（与前端 setMood 语义一致）。 */
    @Nullable
    public String getLastMood() {
        return lastMood;
    }

    /** 流式途中：总是保留尾部 tailSize 字符，防止跨 chunk 切碎下一个可能的标记。 */
    private void flushSafePrefix() {
        int safeLen = buffer.length() - tailSize;
        if (safeLen <= 0) return;
        String safe = buffer.substring(0, safeLen);
        sink.next(new ChatEvent("content", safe));
        buffer.delete(0, safeLen);
    }

    /** 构造 mood 事件 JSON payload；对 keyword 做 JSON 转义。 */
    static String moodPayload(String keyword) {
        return "{\"keyword\":\"" + JsonStrings.escape(keyword) + "\"}";
    }
}
