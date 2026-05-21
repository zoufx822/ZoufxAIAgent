package com.zoufx.ai.agent.tool.impl;

import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.api.HotMemoryType;
import com.zoufx.ai.agent.memory.api.UserImpressionFields;
import com.zoufx.ai.agent.tool.api.ToolPrompt;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户印象更新工具（v0.13 起：HotMemoryUpdateTool 重命名而来）。
 *
 * <p>专管 hot_memory 的 {@code user-impression} type。未来新增 type
 * （如 significant-event）会有各自的专用工具，不挤一起——专用工具
 * 比通用 {@code update_hot_memory(type, key, value)} 在 LLM 准确率上高得多。
 *
 * <p>字段集 + 行为指令的单一来源：{@link UserImpressionFields#FIELDS}。
 * 工具白名单校验直接读它；{@code @Tool} / {@code @P} 注解和 promptInstructions
 * 的字面值需手工与该常量保持一致（同文件内就近一致即可）。
 *
 * <p>线程：LC4J 在工具线程上同步调用 @Tool 方法，故 {@code .block()} 桥接
 * 反应式 {@link HotMemoryStore#set} 是合规的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserImpressionUpdateTool implements ToolPrompt {

    private final HotMemoryStore hotMemoryStore;

    @Override
    public String section() {
        return "用户印象更新";
    }

    @Override
    public String promptInstructions() {
        // 识别规则段 + 白名单字面 由 UserImpressionFields 动态拼接——
        // 加字段时只改 UserImpressionFields.FIELDS，本方法的字面文本无需同步
        return """
                ==必须触发==：识别到对方的任何画像属性时，立刻调用 update_user_impression(key, value)。

                各字段的识别与写入规则：

                %s
                调用规则：
                - key 必须从以下白名单中选：%s
                - value 只传==事实本身==（如 "Java 后端" 而不是 "我是 Java 后端"）
                - 一轮内可调用多次（同时识别多个字段）
                - 调用是后台动作，回复对方时无需说"我已记下"
                - 反模式：
                  - 写白名单外的 key——会被拒绝
                  - 单次零散信号就武断推断（如对方说"今天写了几行代码"不能直接断定职业；
                    需要明确或多次信号才整理写入）
                  - 在回复里说"好的我会记住"但实际==没调工具==
                """.formatted(
                        UserImpressionFields.renderDetectionRules(),
                        UserImpressionFields.whitelistLiteral()
                );
    }

    @Tool("用户印象更新：识别到对方的属性（姓名/语言/职业/兴趣/对话风格）时调用，写入长期记忆。"
            + "包括对方明确告知，也包括对话内容中明确或多次出现的相关信号。")
    public String update_user_impression(
            @ToolMemoryId String userId,
            // 白名单字面通过 UserImpressionFields.WHITELIST_LITERAL 编译期拼接——
            // 加字段时只改 UserImpressionFields 一处，本注解自动跟随
            @P("属性字段名。必须从白名单选：" + UserImpressionFields.WHITELIST_LITERAL) String key,
            @P("属性值。只传事实本身，不带「我是」「我叫」「我在」等前缀") String value) {
        if (key == null || key.isBlank()) {
            return "update_user_impression 调用失败：key 不能为空";
        }
        if (value == null || value.isBlank()) {
            return "update_user_impression 调用失败：value 不能为空";
        }
        String trimmedKey = key.trim();
        String trimmedValue = value.trim();
        if (!UserImpressionFields.FIELDS.containsKey(trimmedKey)) {
            log.warn("⛔ update_user_impression rejected [userId={}] key='{}' not in whitelist", userId, trimmedKey);
            return "update_user_impression 调用失败：key '" + trimmedKey + "' 不在允许字段列表内";
        }
        log.info("📝 update_user_impression [userId={}] {}={}", userId, trimmedKey, trimmedValue);
        hotMemoryStore.set(userId, HotMemoryType.USER_IMPRESSION, trimmedKey, trimmedValue).block();
        return "已记下：" + trimmedKey + "=" + trimmedValue;
    }
}
