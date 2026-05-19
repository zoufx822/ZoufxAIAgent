package com.zoufx.ai.agent.tool.impl;

import com.zoufx.ai.agent.tool.property.UserProfileProperties;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.zoufx.ai.agent.tool.api.ToolPrompt;

/**
 * Hot Memory 写入工具（v1.1 多字段版）。
 *
 * <p>v1 版只有 {@code remember_user_name(name)} 单字段写入。v1.1 升级为通用
 * {@code update_user_profile(key, value)}：LLM 识别到对方告知任何画像属性（称呼/语言/时区
 * /职业/兴趣/对话风格）时调用，按 yml 白名单校验 key 后 UPSERT。
 *
 * <p>线程：LC4J 在工具线程上同步调用 @Tool 方法，故 {@code .block()} 桥接
 * 反应式 {@link HotMemoryStoreContract#set} 是合规的。
 *
 * <p>==删除旧 remember_user_name 工具==——LLM 看到的工具列表越短越好；
 * 在 system prompt 里更新工具说明片段，LLM 自然学到新姿势。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTool implements ToolPrompt {

    private final HotMemoryStore hotMemoryStore;
    private final UserProfileProperties properties;

    @Override
    public String section() {
        return "update_user_profile（记住对方画像）";
    }

    @Override
    public String promptInstructions() {
        return """
                ==必须触发==：只要对方在本轮发言里告知了关于自己的任何画像属性，==立刻调用 update_user_profile(key, value)==。
                必触发的信号示例：
                - 自我介绍 / 自报家门 → key="display_name"
                - 提到所在地、时区、什么时间 → key="timezone"
                - 提到职业、用什么技术栈、做什么工作 → key="role"
                - 提到爱好、兴趣、平时喜欢做什么 → key="interests"
                - 提到期望的对话风格（如"简洁点"、"详细点"）→ key="tone"
                - 提到母语 / 使用的语言 → key="language"

                调用规则：
                - key 必须从以下白名单中选：display_name / language / timezone / role / interests / tone
                - value 只传==事实本身==（如 "Java 后端" 而不是 "我是 Java 后端"）
                - 一轮内可调用多次（如对方同时介绍姓名+职业）
                - 调用是后台动作，回复对方时无需说"我已记下"
                - 反模式：
                  - 写白名单外的 key（如 favorite_pet）——会被拒绝
                  - 替对方猜测——只在对方明确说出时调用
                  - 在回复里说"好的我会记住"但实际==没调工具==
                """;
    }

    @Tool("当对方明确告诉你关于自己的画像属性（称呼/语言/时区/职业/兴趣/对话风格）时，调用此工具写入长期记忆。下次对话起会被识别。")
    public String update_user_profile(
            @ToolMemoryId String userId,
            @P("属性字段名。必须从白名单选：display_name / language / timezone / role / interests / tone") String key,
            @P("属性值。只传事实本身，不带「我是」「我叫」「我在」等前缀") String value) {
        if (key == null || key.isBlank()) {
            return "update_user_profile 调用失败：key 不能为空";
        }
        if (value == null || value.isBlank()) {
            return "update_user_profile 调用失败：value 不能为空";
        }
        String trimmedKey = key.trim();
        String trimmedValue = value.trim();
        if (!properties.getEnabledKeys().contains(trimmedKey)) {
            log.warn("⛔ update_user_profile rejected [userId={}] key='{}' not in whitelist", userId, trimmedKey);
            return "update_user_profile 调用失败：key '" + trimmedKey + "' 不在允许字段列表内";
        }
        log.info("📝 update_user_profile [userId={}] {}={}", userId, trimmedKey, trimmedValue);
        hotMemoryStore.set(userId, trimmedKey, trimmedValue).block();
        return "已记下：" + trimmedKey + "=" + trimmedValue;
    }
}
