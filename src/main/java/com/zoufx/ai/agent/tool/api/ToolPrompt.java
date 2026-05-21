package com.zoufx.ai.agent.tool.api;

/**
 * 工具的 prompt 契约：每个工具自带一段使用说明，由 SystemPromptComposer 拼装到 system prompt。
 * 让工具的"何时调用"、"调用规则"内聚在工具自身，避免散落到 SYSTEM_TEMPLATE。
 */
public interface ToolPrompt {

    /** 工具在 system prompt 里的小节标题，例如 "search_web（网络检索）" */
    String section();

    /**
     * 工具的使用规则正文。
     * <p>v0.13 起：==无任何占位符替换机制==——LLM 已通过系统提示词顶部「当前日期」获得日期，
     * 工具内需要日期时让 LLM 自行组装即可，prompt 内不要写 {today} 之类的字面占位符。
     */
    String promptInstructions();
}
