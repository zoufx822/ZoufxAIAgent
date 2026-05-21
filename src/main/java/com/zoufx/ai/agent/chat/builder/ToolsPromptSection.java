package com.zoufx.ai.agent.chat.builder;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.tool.api.ToolPrompt;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 「## 可用工具」段（v0.13 从 SystemPromptComposer.renderTools 抽出）。
 *
 * <p>聚合所有 {@link ToolPrompt} Bean 渲染。Spring 自动收集——新增工具实现 ToolPrompt
 * 即自动出现在 prompt 中，无需改本类。
 *
 * <p>v0.13 调整：==移除 {@code {today}} 占位符替换机制==——LLM 已通过系统提示词顶部
 * 「当前日期」获得日期，工具 prompt 内无需再做 Java 层字符串替换。本 Section 退化为
 * 单纯字符串拼接，无任何运行时计算。
 *
 * <p>归属：跨工具模块聚合段，归 chat 编排层。
 *
 * <p>注入顺序：order=30。
 */
@Component
@RequiredArgsConstructor
public class ToolsPromptSection implements PromptSection {

    private final List<ToolPrompt> tools;

    @Override
    public int order() {
        return 30;
    }

    @Override
    @Nullable
    public String render(@Nullable String memoryId) {
        if (tools.isEmpty()) return null;
        String body = tools.stream()
                .map(t -> "### " + t.section() + "\n" + t.promptInstructions())
                .collect(Collectors.joining("\n"));
        return "## 可用工具\n\n" + body + "\n";
    }
}
