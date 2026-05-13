package com.zoufx.ai.agent.tool;

import com.zoufx.ai.agent.memory.HotMemoryStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hot Memory 写入工具：当用户主动告知称呼时，LLM 调用此工具把 display_name
 * 晶化到 user_profile 表（UPSERT 语义，后写覆盖前写）。
 *
 * 线程：LC4J 在工具线程上同步调用 @Tool 方法，故 {@code .block()} 桥接
 * 反应式 {@link HotMemoryStore#set} 是合规的。
 *
 * v1 范围只 enable {@code display_name} 一个 key。v2 扩展 language / timezone / role 时
 * 可加更多工具方法（如 remember_language），或重构为单一通用 set 工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTool implements ToolPromptContributor {

    private static final String KEY_DISPLAY_NAME = "display_name";

    private final HotMemoryStore hotMemoryStore;

    @Override
    public String section() {
        return "remember_user_name（记住对方称呼）";
    }

    @Override
    public String promptInstructions() {
        return """
                ==必须触发==：只要对方在本轮发言里告知了自己的称呼/姓名，==立刻调用 remember_user_name(name)==，不要犹豫。
                必触发的信号示例：
                - 「我叫 X」「我是 X」「叫我 X 就行」「你叫我 X」
                - 「X 是我」「（在自我介绍中说出）X」
                - 任何让对方称呼明确具象化的表达

                调用规则：
                - name 只传称呼本身（如 "张三"），不要带"我叫"/"叫我"/"姓"等前缀
                - 不要替对方猜——只在对方明确说出时调用
                - 调用是后台动作，回复对方时无需说"我已记下"——直接用新称呼自然继续对话
                - 反模式：识别到名字却==不调用工具==、或在回复里说"好的我会记住"但实际==没调工具==——这两种都错
                """;
    }

    @Tool("当对方明确告诉你自己的称呼/姓名时，调用此工具把称呼写入长期记忆。下次对话起对方再来时会被识别。")
    public String remember_user_name(
            @ToolMemoryId String userId,
            @P("对方告诉你的称呼本身（不要带「我叫」「叫我」等前缀）") String name) {
        if (name == null || name.isBlank()) {
            return "remember_user_name 调用失败：name 不能为空";
        }
        String trimmed = name.trim();
        log.info("📝 remember_user_name [userId={}] name='{}'", userId, trimmed);
        hotMemoryStore.set(userId, KEY_DISPLAY_NAME, trimmed).block();
        return "已记下对方称呼：" + trimmed;
    }
}
