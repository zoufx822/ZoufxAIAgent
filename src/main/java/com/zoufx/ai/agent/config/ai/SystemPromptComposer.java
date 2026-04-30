package com.zoufx.ai.agent.config.ai;

import com.zoufx.ai.agent.tool.ToolPromptContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * System prompt 的分层组装器。
 *
 * 分四层：
 *   1. 角色（who am I）
 *   2. 全局上下文（当前日期等运行时数据）
 *   3. 工具集（自动从所有 ToolPromptContributor Bean 聚合，{today} 占位符替换）
 *   4. 全局响应规则
 *
 * 每次 chat 调用时由 LangChain4J 触发 compose(memoryId)，注入到 LLM 请求的 system 字段。
 */
@Component
public class SystemPromptComposer {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA);

    private static final String ROLE = "你是中文 AI 助手。";

    private static final String GLOBAL_RULES = """
            静态知识问题（语法、概念、历史常识）直接回答，不要滥用工具。
            """;

    private final List<ToolPromptContributor> tools;

    public SystemPromptComposer(List<ToolPromptContributor> tools) {
        this.tools = tools;
    }

    public Function<Object, String> asProvider() {
        return this::compose;
    }

    public String compose(Object memoryId) {
        String today = LocalDate.now().format(DATE_FMT);

        StringBuilder sb = new StringBuilder();
        sb.append(ROLE).append("\n");
        sb.append("当前日期：").append(today).append("\n\n");

        if (!tools.isEmpty()) {
            sb.append("## 可用工具\n\n");
            sb.append(renderTools(today)).append("\n");
        }

        sb.append("## 响应规则\n\n");
        sb.append(GLOBAL_RULES);

        return sb.toString();
    }

    private String renderTools(String today) {
        return tools.stream()
                .map(t -> "### " + t.section() + "\n"
                        + t.promptInstructions().replace("{today}", today))
                .collect(Collectors.joining("\n"));
    }
}
