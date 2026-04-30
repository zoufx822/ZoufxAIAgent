package com.zoufx.ai.agent.tool;

/**
 * 工具的 prompt 契约：每个工具自带一段使用说明，由 SystemPromptComposer 拼装到 system prompt。
 * 让工具的"何时调用"、"调用规则"内聚在工具自身，避免散落到 SYSTEM_TEMPLATE。
 */
public interface ToolPromptContributor {

    /** 工具在 system prompt 里的小节标题，例如 "search_web（网络检索）" */
    String section();

    /**
     * 工具的使用规则正文。
     * 可使用 {today} 占位符，由 SystemPromptComposer 在拼装时替换为今日日期。
     */
    String promptInstructions();
}
