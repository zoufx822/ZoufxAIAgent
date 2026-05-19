package com.zoufx.ai.agent.chat.config;

import com.zoufx.ai.agent.properties.MoodProperties;
import com.zoufx.ai.agent.tool.property.UserProfileProperties;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.api.MemoryStore;
import com.zoufx.ai.agent.soul.api.SoulStore;
import com.zoufx.ai.agent.soul.property.SoulProperties;
import com.zoufx.ai.agent.tool.api.ToolPrompt;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *   4. 工具集（自动从所有 ToolPrompt Bean 聚合，{today} 占位符替换）
 *   5. 全局响应规则
 *
 * <h2>Frozen Snapshot 约束（v1 显式化）</h2>
 * {@link #compose(Object)} 由 LC4J 作为 SystemPromptProvider 在 ==每次 chat 请求的开始处==
 * 同步内联调用 ==一次==——这是 LC4J 默认行为，单请求 system prompt 自然冻结。
 *
 * ==请勿==在响应流的任何阶段（{@code doOnNext} / 工具回调 / 流尾 hook 等）主动重新调用 compose()，
 * 否则会破坏「Hot Memory 修改要到下次请求才生效」的 Hermes Frozen Snapshot 语义：
 *   T0: 请求开始 → compose() 读到 display_name=null → 注入陌生人迎接
 *   T1: LLM 调 remember_user_name("张三") → user_profile 落盘
 *   T2: LLM 继续输出 "你好张三..."
 *   T3: 响应结束。==整个请求中 system prompt 不重读==
 *   T4: 下次请求 → compose() 重新读到 display_name=张三
 *
 * v1 中此 provider 唯一注入点：{@code AssistantConfig} 把 {@link #asProvider()} 传给
 * {@code AiServices.builder().systemMessageProvider(...)}。新增调用点必须沿用同款约束。
 *
 * <h2>线程上下文</h2>
 * 本方法被 LC4J 作为 SystemPromptProvider 同步契约调用，在调用方线程上内联执行。
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

    /** key → 注入模板。占位 {} 由 value 替换。LinkedHashMap 保留 yml 中 enabled-keys 的对齐顺序。 */
    private static final Map<String, String> KEY_TEMPLATES = new LinkedHashMap<>();
    static {
        // display_name 不在这里——它单独走"称呼锚"段落，不作为普通字段一行注入
        KEY_TEMPLATES.put("language",  "- 对方使用的语言：{}");
        KEY_TEMPLATES.put("timezone",  "- 对方所在时区：{}（涉及时间话题时按这个时区作答）");
        KEY_TEMPLATES.put("role",      "- 对方的身份/职业：{}");
        KEY_TEMPLATES.put("interests", "- 对方的偏好/兴趣：{}");
        KEY_TEMPLATES.put("tone",      "- 对方期望的对话风格：{}");
    }

    private final List<ToolPrompt> tools;
    private final MemoryStore memoryStore;
    private final HotMemoryStore hotMemoryStoreContract;
    private final SoulStore soulStore;
    private final MoodProperties moodProperties;
    private final UserProfileProperties userProfileProperties;
    private final SoulProperties soulProperties;

    public SystemPromptComposer(List<ToolPrompt> tools,
                                MemoryStore memoryStore,
                                HotMemoryStore hotMemoryStoreContract,
                                SoulStore soulStore,
                                MoodProperties moodProperties,
                                UserProfileProperties userProfileProperties,
                                SoulProperties soulProperties) {
        this.tools = tools;
        this.memoryStore = memoryStore;
        this.hotMemoryStoreContract = hotMemoryStoreContract;
        this.soulStore = soulStore;
        this.moodProperties = moodProperties;
        this.userProfileProperties = userProfileProperties;
        this.soulProperties = soulProperties;
    }

    public Function<Object, String> asProvider() {
        return this::compose;
    }

    public String compose(Object memoryId) {
        String today = LocalDate.now().format(DATE_FMT);

        StringBuilder sb = new StringBuilder();
        sb.append(ROLE).append("\n");
        sb.append("当前日期：").append(today).append("\n\n");

        appendSoulSection(sb);

        String userId = memoryId == null ? null : memoryId.toString();
        appendIdentitySection(sb, userId);

        if (!tools.isEmpty()) {
            sb.append("## 可用工具\n\n");
            sb.append(renderTools(today)).append("\n");
        }

        sb.append("## 响应规则\n\n");
        sb.append(GLOBAL_RULES);

        appendMoodSection(sb);

        return sb.toString();
    }

    /**
     * SOUL 段（v1.1）：注入 AI 自身人格 / 风格 / 原则 / 反模式 / 小习惯。
     *
     * 与 UserProfile 对偶 —— UserProfile 是"对方是谁"，SOUL 是"我是谁"。两段都遵循 Frozen Snapshot
     * 约束（v1 第八章）：snapshot 在请求开头读一次，修改要等下次请求才生效。
     *
     * 注入顺序在 ROLE 之后、UserProfile 之前——先确立"我是谁"，再叠加"对方是谁"。
     */
    private void appendSoulSection(StringBuilder sb) {
        List<String> enabled = soulProperties.getEnabledKeys();
        if (enabled == null || enabled.isEmpty()) return;
        Map<String, String> snap = soulStore.snapshot();
        if (snap.isEmpty()) return;

        // 收集已写入的 enabled key，决定是否值得开 "## 关于你自己" 段
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String key : enabled) {
            String v = snap.get(key);
            if (v != null && !v.isBlank()) ordered.put(key, v);
        }
        if (ordered.isEmpty()) return;

        sb.append("## 关于你自己\n\n");
        String name = ordered.get("name");
        String tone = ordered.get("tone");
        if (name != null) {
            sb.append("你叫").append(name).append("。");
            if (tone != null) sb.append("你的说话风格是：").append(tone).append("。");
            sb.append("\n\n");
        } else if (tone != null) {
            sb.append("你的说话风格是：").append(tone).append("。\n\n");
        }
        appendSoulBlock(sb, ordered, "principles",         "你坚持以下表达原则：");
        appendSoulBlock(sb, ordered, "forbidden_patterns", "你**绝不**使用以下表达：");
        appendSoulBlock(sb, ordered, "quirks",             "你有以下小习惯（自然流露即可，不要刻意）：");
    }

    /** SOUL 子块：value 可能是 multi-line（yml 用 |），原样保留缩进/列表符号注入 prompt。 */
    private void appendSoulBlock(StringBuilder sb, Map<String, String> ordered, String key, String title) {
        String value = ordered.get(key);
        if (value == null || value.isBlank()) return;
        sb.append(title).append("\n").append(value);
        if (!value.endsWith("\n")) sb.append("\n");
        sb.append("\n");
    }

    /**
     * 情绪标记段（v1.1）：命令 LLM 在每条回复末尾追加 {@code <!--mood:KEYWORD-->} HTML 注释。
     * 后端 AIChatService 在 content 流尾部用 tail buffer 扫描剥离，独立成 SSE mood 事件。
     *
     * 用命令式 + 反模式枚举，避免 weak model 漏标（参见 v1 第 5.4 节末尾的 prompt 调优坑）。
     */
    private void appendMoodSection(StringBuilder sb) {
        if (!moodProperties.isEnabled()) return;
        List<String> keywords = moodProperties.getKeywords();
        if (keywords == null || keywords.isEmpty()) return;

        sb.append("\n## 情绪标记\n\n")
                .append("在你每条回复的**最末尾**，追加一个 HTML 注释标记你此刻的情绪，格式严格如下：\n")
                .append("<!--mood:KEYWORD-->\n\n")
                .append("KEYWORD 必须从以下词表中精确选择一个：\n")
                .append(String.join(" / ", keywords)).append("\n\n")
                .append("规则：\n")
                .append("- 该注释不会显示给用户，仅供系统读取\n")
                .append("- 务必每条回复都追加，不可省略\n")
                .append("- 必须放在回复的最末尾（在所有正文与标点之后）\n")
                .append("- 反模式：\n")
                .append("  - 不要使用词表以外的词\n")
                .append("  - 不要在注释里加解释（如 <!--mood:好奇，因为这个问题很有趣-->）\n")
                .append("  - 不要把标记夹在正文中间\n");
    }

    /**
     * 身份识别三态分支（v1.1 多字段版）：
     *   - Hot 有 display_name → 注入「关于对方」段：称呼锚 + 按 enabled-keys 顺序逐行注入有值字段
     *   - 无 display_name 但记忆空 → 陌生人迎接
     *   - 无 display_name 但有历史 → 静默，让 LLM 从 ChatMemory 自然接续
     *
     * <p>设计取舍：没有 display_name 时即使其他字段（language/timezone 等）有值也不注入「关于对方」段——
     * 避免给 LLM 既"告知偏好"又被要求"问称呼"的矛盾。display_name 是身份锚。
     */
    private void appendIdentitySection(StringBuilder sb, String userId) {
        if (userId == null) return;

        Map<String, String> snapshot = hotMemoryStoreContract.snapshot(userId);
        String displayName = snapshot.get(KEY_DISPLAY_NAME);
        if (displayName != null && !displayName.isBlank()) {
            sb.append("## 关于对方\n\n");
            sb.append("对方的称呼是「").append(displayName).append("」。")
                    .append("这是你已经认识的人——\n")
                    .append("- 直接称呼对方为「").append(displayName).append("」，不要问名字、不要确认；\n")
                    .append("- 每轮回复中至少自然地使用一次这个称呼（除非对方明确问你别叫他名字）；\n")
                    .append("- 如果对方问「我是谁」「你还记得我吗」之类的问题，明确回答对方就是「")
                    .append(displayName).append("」，不要敷衍说「刚开始聊天」「没有记录」。\n");

            // 多字段：按 enabled-keys 顺序逐行注入有值字段；display_name 上面已经处理，跳过
            for (String key : userProfileProperties.getEnabledKeys()) {
                if (KEY_DISPLAY_NAME.equals(key)) continue;
                String template = KEY_TEMPLATES.get(key);
                if (template == null) continue;  // 未知 key（yml 配错或预留），跳过
                String value = snapshot.get(key);
                if (value == null || value.isBlank()) continue;
                sb.append(template.replace("{}", value)).append("\n");
            }
            sb.append("\n");
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
