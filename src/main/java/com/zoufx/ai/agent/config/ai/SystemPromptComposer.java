package com.zoufx.ai.agent.config.ai;

import com.zoufx.ai.agent.memory.HotMemoryStore;
import com.zoufx.ai.agent.memory.MemoryStore;
import com.zoufx.ai.agent.tool.ToolPromptContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * System prompt 的分层组装器。
 *
 * 分层：
 *   1. 角色（who am I）
 *   2. 全局上下文（当前日期等运行时数据）
 *   3. 身份识别（v1 起三态）：
 *      - Hot Memory 有 display_name → 注入"对方叫 X"片段，跳过陌生人迎接
 *      - 无 display_name 但记忆为空 → 陌生人迎接，主动询问称呼
 *      - 无 display_name 但有历史对话 → 不注入额外身份片段（已认识但还没告知名字）
 *   4. 工具集（自动从所有 ToolPromptContributor Bean 聚合，{today} 占位符替换）
 *   5. 全局响应规则
 *
 * 每次 chat 调用时由 LangChain4J 触发 compose(memoryId)，注入到 LLM 请求的 system 字段。
 *
 * 线程上下文：本方法被 LC4J 作为 SystemPromptProvider 同步契约调用，在调用方线程上内联执行。
 * 上层从 WebFlux event loop 触发 chat，故 compose 也在 event loop 上跑——==所有 store 读取必须用同步签名==，
 * 不能 .block() 一个 Mono，否则被 Reactor NonBlockingHook 拦下抛错。
 */
@Component
public class SystemPromptComposer {

    /** v1 Hot Memory 唯一启用的 key。v2 会改为从 yml 读取 enabled-keys 列表。 */
    private static final String KEY_DISPLAY_NAME = "display_name";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA);

    private static final String ROLE = "你是一个和人类一样有智慧和记忆的智能体。";

    private static final String GLOBAL_RULES = """
            静态知识问题（语法、概念、历史常识）直接回答，不要滥用工具。
            """;

    private static final String STRANGER_GREETING = """
            你目前不认识对方（没有任何过往对话）。请像一位礼貌的人那样：
            - 自然地融入"问好 + 询问称呼"，不要打断对方的诉求；
            - 如果对方直接抛出问题，先简短回答，再以一句话追问"方便先问下怎么称呼您？"；
            - 不要重复"你好"或机械地反复要名字——一次询问就够。
            """;

    private final List<ToolPromptContributor> tools;
    private final MemoryStore memoryStore;
    private final HotMemoryStore hotMemoryStore;

    public SystemPromptComposer(List<ToolPromptContributor> tools,
                                MemoryStore memoryStore,
                                HotMemoryStore hotMemoryStore) {
        this.tools = tools;
        this.memoryStore = memoryStore;
        this.hotMemoryStore = hotMemoryStore;
    }

    public Function<Object, String> asProvider() {
        return this::compose;
    }

    public String compose(Object memoryId) {
        String today = LocalDate.now().format(DATE_FMT);

        StringBuilder sb = new StringBuilder();
        sb.append(ROLE).append("\n");
        sb.append("当前日期：").append(today).append("\n\n");

        String userId = memoryId == null ? null : memoryId.toString();
        appendIdentitySection(sb, userId);

        if (!tools.isEmpty()) {
            sb.append("## 可用工具\n\n");
            sb.append(renderTools(today)).append("\n");
        }

        sb.append("## 响应规则\n\n");
        sb.append(GLOBAL_RULES);

        return sb.toString();
    }

    /**
     * 身份识别三态分支：Hot 命中 → 注入称呼；记忆空 → 陌生人；既无 display_name 又有历史 → 静默。
     */
    private void appendIdentitySection(StringBuilder sb, String userId) {
        if (userId == null) return;

        Optional<String> displayName = hotMemoryStore.get(userId, KEY_DISPLAY_NAME);
        if (displayName.isPresent()) {
            sb.append("## 关于对方\n\n");
            sb.append("对方的称呼是「").append(displayName.get()).append("」。")
                    .append("这是你已经认识的人——\n")
                    .append("- 直接称呼对方为「").append(displayName.get()).append("」，不要问名字、不要确认；\n")
                    .append("- 每轮回复中至少自然地使用一次这个称呼（除非对方明确问你别叫他名字）；\n")
                    .append("- 如果对方问「我是谁」「你还记得我吗」之类的问题，明确回答对方就是「")
                    .append(displayName.get()).append("」，不要敷衍说「刚开始聊天」「没有记录」。\n\n");
            return;
        }
        if (memoryStore.isEmpty(userId)) {
            sb.append("## 身份识别\n\n");
            sb.append(STRANGER_GREETING).append("\n");
        }
        // else: 有历史对话但未告知名字——LLM 从 ChatMemory 自己判断接续，不再额外注入
    }

    private String renderTools(String today) {
        return tools.stream()
                .map(t -> "### " + t.section() + "\n"
                        + t.promptInstructions().replace("{today}", today))
                .collect(Collectors.joining("\n"));
    }
}
