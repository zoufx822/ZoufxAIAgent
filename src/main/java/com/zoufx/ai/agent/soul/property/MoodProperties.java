package com.zoufx.ai.agent.soul.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * mood 情感词系统的配置（v0.11 引入；v0.13 yml 路径 ai.mood → ai.soul.mood，Java 类从 chat 挪 soul）。
 *
 * <p>mood 由主 LLM 在每条回复末尾顺带输出 {@code <!--mood:KEYWORD-->} HTML 注释，
 * 后端在 content 流尾部用 tail buffer 扫描 + 剥离，独立成 SSE {@code mood} 事件发给前端。
 *
 * <p>词表受限——LLM 必须从 {@link #keywords} 里精确选一个。词表写 yml 便于扩展，
 * ==修改后需要重启==（因为 system prompt 注入词表，Frozen Snapshot 下次请求才生效）。
 *
 * <p><b>v0.13 命名归类</b>：mood 概念上是 SOUL（人格）的延伸——
 * 情绪谱也是"这个 AI 能展现什么样的内在状态"的一部分。所以 yml 路径从 {@code ai.mood}
 * 挪到 {@code ai.soul.mood}，Java 类从 {@code chat/property/} 挪到 {@code soul/property/}。
 *
 * <p>但==不与 SOUL 字段强行同构==：mood 是==动态 state==（每轮回复都变，走 SSE 实时事件路径），
 * SOUL 字段是==静态 trait==（写一次基本不动，走 system prompt 注入路径，统一是纯文本）。
 * mood 字段是异构 {@code boolean enabled / List<String> keywords / int tailBufferSize}，
 * 不属于 SOUL 的"被 LLM 读的自由文本"语义空间——不进 {@code enabled-keys / seed} 体系，
 * 仅 yml 路径归类表达血缘关系。
 *
 * <p>处理流程上，{@link com.zoufx.ai.agent.chat.builder.MoodPromptSection} 与
 * mood 流剥离器等仍归 chat 包（mood 的"流"性质与 chat 编排紧密相关）。
 */
@Data
@ConfigurationProperties(prefix = "ai.soul.mood")
public class MoodProperties {

    /** 总开关。false 时不注入情绪标记指令、不剥离 mood、不发 mood 事件——纯 v0.1 行为。 */
    private boolean enabled = true;

    /** 词表（v0.11 起步 10 词）。LLM 必须严格从中选一个。 */
    private List<String> keywords = List.of(
            "好奇", "温和", "严肃", "平静", "共情",
            "戏谑", "困惑", "疲惫", "兴奋", "挫败"
    );

    /**
     * content 流尾部维护的扫描窗口长度（字符）。
     * 必须 ≥ {@code <!--mood:最长词-->} 总长——按当前词表 2 字符词最长，约 18 字符足够；
     * 留 32 给未来词表扩展余量。
     */
    private int tailBufferSize = 32;
}
