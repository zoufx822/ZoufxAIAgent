package com.zoufx.ai.agent.service;

import com.zoufx.ai.agent.model.ChatEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 LLM content 流里剥离 {@code <!--mood:KEYWORD-->} 标记（v1.1）。
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
public class MoodStripper {

    /** 匹配 {@code <!--mood:任何非 > 字符-->}，非贪婪。 */
    private static final Pattern MOOD = Pattern.compile("<!--mood:([^>]+?)-->");

    private final int tailSize;
    private final FluxSink<ChatEvent> sink;
    private final String userId;
    private final StringBuilder buffer = new StringBuilder();
    private boolean moodEmitted = false;

    public MoodStripper(int tailSize, FluxSink<ChatEvent> sink, String userId) {
        this.tailSize = tailSize;
        this.sink = sink;
        this.userId = userId;
    }

    /** 接收 LC4J 增量 token；可能 emit 0..1 content 事件 + 0..1 mood 事件。 */
    public void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        buffer.append(chunk);
        if (!moodEmitted) scanAndStrip();
        flushSafePrefix();
    }

    /** 流末尾兜底：再扫一次（标记可能正好在末尾）+ flush 全部剩余内容。 */
    public void flush() {
        if (!moodEmitted) scanAndStrip();
        if (buffer.length() > 0) {
            sink.next(new ChatEvent("content", buffer.toString()));
            buffer.setLength(0);
        }
    }

    /** 扫描 buffer 内的 mood 标记并剥离。命中：emit 命中前内容 + 删除标记 + 发 mood 事件。 */
    private void scanAndStrip() {
        Matcher m = MOOD.matcher(buffer);
        if (!m.find()) return;
        String mood = m.group(1).trim();
        String before = buffer.substring(0, m.start());
        if (!before.isEmpty()) {
            sink.next(new ChatEvent("content", before));
        }
        buffer.delete(0, m.end());
        sink.next(new ChatEvent("mood", moodPayload(mood)));
        moodEmitted = true;
        log.info("Mood stripped [userId={}] mood={}", userId, mood);
    }

    /** 流式途中：mood 未发时留尾防跨 chunk 切碎；mood 已发后全 emit。 */
    private void flushSafePrefix() {
        int tail = moodEmitted ? 0 : tailSize;
        int safeLen = buffer.length() - tail;
        if (safeLen <= 0) return;
        String safe = buffer.substring(0, safeLen);
        sink.next(new ChatEvent("content", safe));
        buffer.delete(0, safeLen);
    }

    /** 构造 mood 事件 JSON payload；对 keyword 做 JSON 转义。 */
    static String moodPayload(String keyword) {
        return "{\"keyword\":\"" + escape(keyword) + "\"}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
