package com.zoufx.ai.agent.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * mood 情感词系统的配置（v1.1）。
 *
 * <p>mood 由主 LLM 在每条回复末尾顺带输出 {@code <!--mood:KEYWORD-->} HTML 注释，
 * 后端在 content 流尾部用 tail buffer 扫描 + 剥离，独立成 SSE {@code mood} 事件发给前端。
 *
 * <p>词表受限——LLM 必须从 {@link #keywords} 里精确选一个。词表写 yml 便于扩展，
 * ==修改后需要重启==（因为 system prompt 注入词表，Frozen Snapshot 下次请求才生效）。
 */
@Data
@ConfigurationProperties(prefix = "ai.mood")
public class MoodProperties {

    /** 总开关。false 时不注入情绪标记指令、不剥离 mood、不发 mood 事件——纯 v1 行为。 */
    private boolean enabled = true;

    /** 词表（v1.1 起步 10 词）。LLM 必须严格从中选一个。 */
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
